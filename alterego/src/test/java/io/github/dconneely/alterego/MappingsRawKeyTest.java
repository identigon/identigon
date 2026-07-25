package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dconneely.alterego.store.InMemoryMappingStore;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@code context.mappings()} ({@link DefaultMappings}) honours {@code rawMappingKeys} exactly
 * like {@code stored()}/{@code unique()} (spec section 2.6, section 5.1) — a dedicated test since
 * this path isn't exercised by either decorator.
 */
class MappingsRawKeyTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final String DOMAIN = "test:mappings";
  private static final String CANONICAL_KEY = "my-canonical-key";

  private static final Strategy<String> WRITES_A_MAPPING =
      (in, ctx) -> {
        ctx.mappings().putIfAbsent(CANONICAL_KEY, "my-value");
        return in;
      };

  @Test
  void usesHashedKeysByDefault() {
    InMemoryMappingStore store = new InMemoryMappingStore();
    AlterEgo eg = AlterEgo.builder().salt(SALT).mappingStore(store).build();
    eg.bind(DOMAIN, WRITES_A_MAPPING).apply("x");

    String expectedHashedKey = Derivation.mapKey(SALT, DOMAIN, CANONICAL_KEY, false);
    assertEquals(Optional.of("my-value"), store.get(DOMAIN, expectedHashedKey));
    assertEquals(Optional.empty(), store.get(DOMAIN, CANONICAL_KEY));
  }

  @Test
  void usesRawCanonicalKeyWhenConfigured() {
    InMemoryMappingStore store = new InMemoryMappingStore();
    AlterEgo eg = AlterEgo.builder().salt(SALT).mappingStore(store).rawMappingKeys(true).build();
    eg.bind(DOMAIN, WRITES_A_MAPPING).apply("x");

    assertEquals(Optional.of("my-value"), store.get(DOMAIN, CANONICAL_KEY));
  }
}
