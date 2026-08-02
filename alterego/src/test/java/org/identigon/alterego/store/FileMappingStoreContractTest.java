package org.identigon.alterego.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

class FileMappingStoreContractTest extends MappingStoreContractTest {

  private final List<FileMappingStore> stores = new ArrayList<>();
  private Path tempDir;

  @BeforeEach
  void setUp() throws IOException {
    tempDir = Files.createTempDirectory("file-mapping-store-contract-test");
  }

  @AfterEach
  void tearDown() throws IOException {
    for (FileMappingStore store : stores) {
      store.close();
    }
    if (tempDir != null && Files.exists(tempDir)) {
      try (Stream<Path> walk = Files.walk(tempDir)) {
        walk.sorted(Comparator.reverseOrder())
            .forEach(p -> {
              try {
                Files.delete(p);
              } catch (IOException ignored) {}
            });
      }
    }
  }

  @Override
  protected MappingStore createStore() {
    try {
      Path file = Files.createTempFile(tempDir, "store", ".txt");
      FileMappingStore store = FileMappingStore.open(file);
      stores.add(store);
      return store;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
