package io.github.dconneely.alterego.store;

import io.github.dconneely.alterego.AlterEgoStoreException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A persistent {@link MappingStore} backed by a single local file.
 * <p>
 * This store is single-process: an exclusive file lock prevents sharing across processes.
 * It grows by exactly one line per distinct stored mapping, and provides durability against
 * process crashes. One instance can be safely shared by multiple threads or {@code AlterEgo}
 * instances in the same process.
 */
public final class FileMappingStore implements MappingStore, AutoCloseable {

  private static final String HEADER = "alterego-mapping-store 1";
  private static final byte[] HEADER_BYTES = (HEADER + "\n").getBytes(StandardCharsets.UTF_8);

  /** One namespace's forward (key to value) and inverse (value to key) maps. */
  private static final class Namespace {
    private final Map<String, String> forward = new ConcurrentHashMap<>();
    private final Map<String, String> inverse = new ConcurrentHashMap<>();
  }

  private final Path file;
  private final FileChannel channel;
  private final FileLock lock;
  private final ConcurrentMap<String, Namespace> namespaces = new ConcurrentHashMap<>();
  private final Object writeMonitor = new Object();
  private volatile boolean closed = false;

  private FileMappingStore(Path file, FileChannel channel, FileLock lock) {
    this.file = file;
    this.channel = channel;
    this.lock = lock;
  }

  /**
   * Opens the file-backed mapping store at the given path, creating it if it does not exist.
   * Acquires an exclusive file lock held until {@link #close()} is called.
   *
   * @param file the path to the store file
   * @return a new {@link FileMappingStore} instance
   * @throws AlterEgoStoreException if the file cannot be opened, is locked by another process, or is malformed
   */
  public static FileMappingStore open(Path file) {
    FileChannel channel = null;
    FileLock lock = null;
    try {
      channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
      try {
        lock = channel.tryLock();
      } catch (java.nio.channels.OverlappingFileLockException e) {
        // Locked by another thread in this JVM
        lock = null;
      }
      if (lock == null) {
        throw new AlterEgoStoreException("File is already locked by another process: " + file);
      }
      
      FileMappingStore store = new FileMappingStore(file, channel, lock);
      store.replay();
      return store;
    } catch (IOException e) {
      if (lock != null) {
        try { lock.release(); } catch (IOException ignored) {}
      }
      if (channel != null) {
        try { channel.close(); } catch (IOException ignored) {}
      }
      throw new AlterEgoStoreException("Failed to open file-backed store: " + file, e);
    } catch (AlterEgoStoreException e) {
      if (lock != null) {
        try { lock.release(); } catch (IOException ignored) {}
      }
      if (channel != null) {
        try { channel.close(); } catch (IOException ignored) {}
      }
      throw e;
    }
  }

  private void replay() throws IOException {
    long size = channel.size();
    if (size == 0) {
      channel.write(ByteBuffer.wrap(HEADER_BYTES));
      return;
    }
    
    // Read whole file for replay. File footprint matches in-memory map footprint, so memory is sufficient.
    ByteBuffer buffer = ByteBuffer.allocate((int) size);
    while (buffer.hasRemaining()) {
      int read = channel.read(buffer);
      if (read == -1) break;
    }
    buffer.flip();
    
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    
    int start = 0;
    int lineNum = 1;
    long validBytes = 0;

    while (start < bytes.length) {
      int end = start;
      while (end < bytes.length && bytes[end] != '\n') {
        end++;
      }
      
      if (end == bytes.length) {
        // Torn tail (no '\n')
        break; // Ignore and let validBytes truncate it on next write
      }
      
      String line = new String(bytes, start, end - start, StandardCharsets.UTF_8);
      
      if (lineNum == 1) {
        if (!HEADER.equals(line)) {
          throw new AlterEgoStoreException("Wrong header in file " + file + " at line 1: " + line);
        }
      } else {
        String[] parts = line.split("\t", -1);
        if (parts.length != 3) {
          throw new AlterEgoStoreException("Malformed line in file " + file + " at line " + lineNum);
        }
        String namespaceName = parts[0];
        String key;
        String value;
        try {
          key = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
          value = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
          throw new AlterEgoStoreException("Invalid base64 in file " + file + " at line " + lineNum, e);
        }
        
        Namespace ns = namespace(namespaceName);
        if (ns.forward.containsKey(key)) {
          throw new AlterEgoStoreException("Duplicate key in file " + file + " at line " + lineNum + ": namespace=" + namespaceName);
        }
        ns.forward.put(key, value);
        ns.inverse.put(value, key);
      }
      
      start = end + 1;
      validBytes = start;
      lineNum++;
    }
    
    // Position channel exactly after the last valid newline so we overwrite any torn tail
    channel.truncate(validBytes);
    channel.position(validBytes);
  }

  private Namespace namespace(String name) {
    return namespaces.computeIfAbsent(name, ignored -> new Namespace());
  }

  private void checkNotClosed() {
    if (closed) {
      throw new AlterEgoStoreException("Store is closed");
    }
  }

  @Override
  public Optional<String> get(String namespace, String key) {
    checkNotClosed();
    return Optional.ofNullable(namespace(namespace).forward.get(key));
  }

  @Override
  public String putIfAbsent(String namespace, String key, String value) {
    checkNotClosed();
    Namespace ns = namespace(namespace);
    // Reuse namespace monitor, same as InMemoryMappingStore
    synchronized (ns) { 
      String existing = ns.forward.get(key);
      if (existing != null) {
        return existing;
      }
      appendRecord(namespace, key, value);
      ns.forward.put(key, value);
      ns.inverse.put(value, key);
      return value;
    }
  }

  @Override
  public PutUniqueResult putIfAbsentUnique(String namespace, String key, String value) {
    checkNotClosed();
    Namespace ns = namespace(namespace);
    synchronized (ns) {
      String existingForKey = ns.forward.get(key);
      if (existingForKey != null) {
        return new PutUniqueResult.ExistingMapping(existingForKey);
      }
      if (ns.inverse.containsKey(value)) {
        return new PutUniqueResult.ValueTaken();
      }
      appendRecord(namespace, key, value);
      ns.forward.put(key, value);
      ns.inverse.put(value, key);
      return new PutUniqueResult.Stored();
    }
  }

  private void appendRecord(String namespace, String key, String value) {
    String encodedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    String encodedValue = Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    String line = namespace + "\t" + encodedKey + "\t" + encodedValue + "\n";
    byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
    
    synchronized (writeMonitor) {
      checkNotClosed();
      try {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
      } catch (IOException e) {
        throw new AlterEgoStoreException("Failed to write to file " + file, e);
      }
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    synchronized (writeMonitor) {
      closed = true;
      if (lock != null) {
        try { lock.release(); } catch (IOException ignored) {}
      }
      if (channel != null) {
        try { channel.close(); } catch (IOException ignored) {}
      }
    }
  }
}
