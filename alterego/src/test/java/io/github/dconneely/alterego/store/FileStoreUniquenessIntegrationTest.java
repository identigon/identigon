package io.github.dconneely.alterego.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dconneely.alterego.AlterEgo;
import io.github.dconneely.alterego.Transformation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStoreUniquenessIntegrationTest {

  @Test
  void uniqueCollisionResolutionsSurviveAcrossRuns(@TempDir Path tempDir) {
    Path file = tempDir.resolve("mappings.alterego");
    byte[] salt = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

    Map<String, String> firstRunOutputs = new HashMap<>();

    // First run
    try (FileMappingStore store = FileMappingStore.open(file)) {
      AlterEgo alterego = AlterEgo.builder().salt(salt).mappingStore(store).build();

      // Deliberately tiny output space (0-9) to force collisions
      Transformation<String> t = alterego.pattern("D").unique();

      for (int i = 0; i < 10; i++) {
        String input = "user-" + i;
        String output = t.apply(input);
        firstRunOutputs.put(input, output);
      }
    }

    // Second run, fresh AlterEgo, same salt and file
    try (FileMappingStore store = FileMappingStore.open(file)) {
      AlterEgo alterego = AlterEgo.builder().salt(salt).mappingStore(store).build();

      Transformation<String> t = alterego.pattern("D").unique();

      for (int i = 0; i < 10; i++) {
        String input = "user-" + i;
        String output = t.apply(input);

        // Assert that every mapping, including collision resolutions, is identical
        assertEquals(firstRunOutputs.get(input), output, "Mapping for " + input + " changed across runs!");
      }
    }
  }
}
