package org.identigon.alterego.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.identigon.alterego.AlterEgoStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileMappingStoreTest {

  @Test
  void newFileWritesHeaderAndIsEmpty(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("store.txt");
    try (FileMappingStore store = FileMappingStore.open(file)) {
      assertTrue(Files.exists(file));
      assertTrue(store.get("ns", "k1").isEmpty());
    }
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    assertEquals(1, lines.size());
    assertEquals("alterego-mapping-store 1", lines.get(0));
  }

  @Test
  void persistenceSurvivesCloseAndReopen(@TempDir Path tempDir) {
    Path file = tempDir.resolve("store.txt");
    try (FileMappingStore store = FileMappingStore.open(file)) {
      assertEquals("v1", store.putIfAbsent("ns1", "k1", "v1"));
      assertEquals(new MappingStore.PutUniqueResult.Stored(), store.putIfAbsentUnique("ns2", "k2", "v2"));
    }
    try (FileMappingStore store = FileMappingStore.open(file)) {
      assertEquals("v1", store.get("ns1", "k1").orElse(null));
      assertEquals("v2", store.get("ns2", "k2").orElse(null));
      // uniqueness survives:
      assertTrue(store.putIfAbsentUnique("ns2", "k3", "v2") instanceof MappingStore.PutUniqueResult.ValueTaken);
    }
  }

  @Test
  void redundantPutsAppendNothing(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("store.txt");
    try (FileMappingStore store = FileMappingStore.open(file)) {
      store.putIfAbsent("ns", "k1", "v1");
      long sizeBefore = Files.size(file);

      // Existing mapping
      assertEquals("v1", store.putIfAbsent("ns", "k1", "v2"));
      assertTrue(store.putIfAbsentUnique("ns", "k1", "v3") instanceof MappingStore.PutUniqueResult.ExistingMapping);

      // Value taken
      store.putIfAbsentUnique("ns", "k2", "v2");
      assertTrue(store.putIfAbsentUnique("ns", "k3", "v2") instanceof MappingStore.PutUniqueResult.ValueTaken);

      long sizeAfter = Files.size(file);
      // Only 2 mappings actually written (k1->v1 and k2->v2)
      assertEquals(sizeBefore + getLineLength("ns", "k2", "v2"), sizeAfter);
    }
  }

  private long getLineLength(String ns, String k, String v) {
    String ek = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(k.getBytes(StandardCharsets.UTF_8));
    String ev = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(v.getBytes(StandardCharsets.UTF_8));
    return (ns + "\t" + ek + "\t" + ev + "\n").getBytes(StandardCharsets.UTF_8).length;
  }

  @Test
  void tornTailIsIgnoredAndOverwritten(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("store.txt");
    try (FileMappingStore store = FileMappingStore.open(file)) {
      store.putIfAbsent("ns", "k1", "v1");
    }

    Files.write(file, "torn-record-no-newline".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

    try (FileMappingStore store = FileMappingStore.open(file)) {
      assertEquals("v1", store.get("ns", "k1").orElse(null));
      store.putIfAbsent("ns", "k2", "v2");
    }

    // The torn record should have been overwritten by k2->v2
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    assertEquals(3, lines.size()); // Header, k1, k2
    assertFalse(lines.stream().anyMatch(l -> l.contains("torn-record")));
  }

  @Test
  void corruptionThrowsOnOpen(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("store.txt");

    // Wrong header
    Files.writeString(file, "wrong-header\n", StandardCharsets.UTF_8);
    assertThrows(AlterEgoStoreException.class, () -> FileMappingStore.open(file));

    // Malformed interior
    Files.writeString(file, "alterego-mapping-store 1\nbadline\n", StandardCharsets.UTF_8);
    assertThrows(AlterEgoStoreException.class, () -> FileMappingStore.open(file));

    // Duplicate key
    try (FileMappingStore store = FileMappingStore.open(tempDir.resolve("good.txt"))) {
      store.putIfAbsent("ns", "k1", "v1");
    }
    List<String> goodLines = Files.readAllLines(tempDir.resolve("good.txt"), StandardCharsets.UTF_8);
    Files.writeString(file, "alterego-mapping-store 1\n" + goodLines.get(1) + "\n" + goodLines.get(1) + "\n", StandardCharsets.UTF_8);
    assertThrows(AlterEgoStoreException.class, () -> FileMappingStore.open(file));
  }

  @Test
  void lockingPreventsConcurrentOpen(@TempDir Path tempDir) {
    Path file = tempDir.resolve("store.txt");
    try (FileMappingStore ignored1 = FileMappingStore.open(file)) {
      assertThrows(AlterEgoStoreException.class, () -> FileMappingStore.open(file));
    }
    // After close, it succeeds
    try (FileMappingStore ignored2 = FileMappingStore.open(file)) {
      // successfully opened
    }
  }

  @Test
  void closedStoreRejectsOperations(@TempDir Path tempDir) {
    Path file = tempDir.resolve("store.txt");
    FileMappingStore store = FileMappingStore.open(file);
    store.close();
    store.close(); // Idempotent

    assertThrows(AlterEgoStoreException.class, () -> store.get("ns", "k"));
    assertThrows(AlterEgoStoreException.class, () -> store.putIfAbsent("ns", "k", "v"));
    assertThrows(AlterEgoStoreException.class, () -> store.putIfAbsentUnique("ns", "k", "v"));
  }

  @Test
  void weirdCharactersRoundTripIntact(@TempDir Path tempDir) {
    Path file = tempDir.resolve("store.txt");
    String key = "key\twith\nnewline and \uD83D\uDE00";
    String value = "val\twith\nnewline and \uD83D\uDE00";

    try (FileMappingStore store = FileMappingStore.open(file)) {
      store.putIfAbsent("ns", key, value);
    }
    try (FileMappingStore store = FileMappingStore.open(file)) {
      assertEquals(value, store.get("ns", key).orElse(null));
    }
  }
}
