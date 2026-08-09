package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.identigon.alterego.store.InMemoryMappingStore;
import org.junit.jupiter.api.Test;

/**
 * The {@code AutoCloseable} salt lifecycle (SPECIFICATION.md section 2, "Lifecycle"): {@code
 * close()}/{@code destroy()} zero the salt, later factory calls throw, and — because every
 * transformation shares the instance's one salt array — applying a transformation built before
 * {@code close()} throws rather than silently deriving from the zeroed salt.
 */
class LifecycleTest {

  private static final byte[] SALT = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).build();
  }

  @Test
  void factoryCallsAfterCloseThrow() {
    AlterEgo alterego = alterego();
    alterego.close();
    assertThrows(IllegalStateException.class, alterego::firstName);
    assertThrows(IllegalStateException.class, () -> alterego.pattern("DDD"));
  }

  @Test
  void destroyIsAnAliasForClose() {
    AlterEgo alterego = alterego();
    alterego.destroy();
    assertThrows(IllegalStateException.class, alterego::firstName);
  }

  @Test
  void closeIsIdempotent() {
    AlterEgo alterego = alterego();
    alterego.close();
    alterego.close();
    assertThrows(IllegalStateException.class, alterego::firstName);
  }

  @Test
  void tryWithResourcesClosesAndZeroesSalt() {
    byte[] salt = SALT.clone();
    AlterEgo escaped;
    try (AlterEgo alterego = AlterEgo.builder().salt(salt).build()) {
      escaped = alterego;
      alterego.lastName().apply("Smith"); // succeeds while open
    }
    assertThrows(IllegalStateException.class, escaped::lastName);
  }

  @Test
  void applyingATransformationBuiltBeforeCloseThrows() {
    AlterEgo alterego = alterego();
    Transformation<String> lastName = alterego.lastName();
    String whileOpen = lastName.apply("Smith"); // succeeds while open
    assertEquals(whileOpen, lastName.apply("Smith")); // deterministic while open
    alterego.close();
    // Must fail loud on the zeroed salt, not silently return a wrong (all-zero-salt) output.
    assertThrows(IllegalStateException.class, () -> lastName.apply("Smith"));
  }

  @Test
  void applyingADecoratedTransformationAfterCloseThrows() {
    AlterEgo alterego =
        AlterEgo.builder().salt(SALT).mappingStore(new InMemoryMappingStore()).build();
    Transformation<String> unique = alterego.pattern("LLDDDD").unique();
    unique.apply("seed");
    alterego.close();
    assertThrows(IllegalStateException.class, () -> unique.apply("another"));
  }
}
