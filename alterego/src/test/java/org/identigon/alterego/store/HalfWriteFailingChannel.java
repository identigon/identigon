package org.identigon.alterego.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * A {@link FileChannel} that delegates everything, except that once {@link #arm()}ed its next
 * {@link #write(ByteBuffer)} writes only half the buffer and then fails - a disk filling up
 * mid-record.
 */
final class HalfWriteFailingChannel extends FileChannel {

  private final FileChannel delegate;
  private boolean armed;

  HalfWriteFailingChannel(FileChannel delegate) {
    this.delegate = delegate;
  }

  void arm() {
    armed = true;
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    if (!armed) {
      return delegate.write(src);
    }
    armed = false;
    ByteBuffer half = src.duplicate();
    half.limit(src.position() + src.remaining() / 2);
    delegate.write(half);
    throw new IOException("No space left on device (simulated)");
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    return delegate.read(dst);
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    return delegate.read(dsts, offset, length);
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    return delegate.write(srcs, offset, length);
  }

  @Override
  public long position() throws IOException {
    return delegate.position();
  }

  @Override
  public FileChannel position(long newPosition) throws IOException {
    delegate.position(newPosition);
    return this;
  }

  @Override
  public long size() throws IOException {
    return delegate.size();
  }

  @Override
  public FileChannel truncate(long size) throws IOException {
    delegate.truncate(size);
    return this;
  }

  @Override
  public void force(boolean metaData) throws IOException {
    delegate.force(metaData);
  }

  @Override
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
    return delegate.transferTo(position, count, target);
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
    return delegate.transferFrom(src, position, count);
  }

  @Override
  public int read(ByteBuffer dst, long position) throws IOException {
    return delegate.read(dst, position);
  }

  @Override
  public int write(ByteBuffer src, long position) throws IOException {
    return delegate.write(src, position);
  }

  @Override
  public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
    return delegate.map(mode, position, size);
  }

  @Override
  public FileLock lock(long position, long size, boolean shared) throws IOException {
    return delegate.lock(position, size, shared);
  }

  @Override
  public FileLock tryLock(long position, long size, boolean shared) throws IOException {
    return delegate.tryLock(position, size, shared);
  }

  @Override
  protected void implCloseChannel() throws IOException {
    delegate.close();
  }
}
