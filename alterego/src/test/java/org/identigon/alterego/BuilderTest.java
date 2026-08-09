package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class BuilderTest {

  private static final byte[] VALID_SALT = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

  @Test
  void requiresSalt() {
    AlterEgoConfigException ex =
        assertThrows(AlterEgoConfigException.class, () -> AlterEgo.builder().build());
    assertTrue(ex.getMessage().contains("salt"));
  }

  @Test
  void rejectsSaltShorterThan16Bytes() {
    byte[] shortSalt = "short".getBytes(StandardCharsets.UTF_8);
    assertThrows(
        AlterEgoConfigException.class, () -> AlterEgo.builder().salt(shortSalt).build());
  }

  @Test
  void accepts16ByteSalt() {
    byte[] salt = new byte[16];
    AlterEgo.builder().salt(salt).build();
  }

  @Test
  void acceptsLongerSalt() {
    byte[] salt = new byte[32];
    AlterEgo.builder().salt(salt).build();
  }

  @Test
  void charSaltConvertsViaUtf8() {
    char[] chars = "0123456789abcdef".toCharArray();
    AlterEgo.builder().salt(chars).build();
  }

  @Test
  void rejectsShortCharSalt() {
    char[] chars = "short".toCharArray();
    assertThrows(AlterEgoConfigException.class, () -> AlterEgo.builder().salt(chars).build());
  }

  @Test
  void defaultsLocaleToUk() {
    // No direct getter on AlterEgo for locale; verified indirectly via TransformationContext
    // in BindingTest. This test just confirms build() succeeds without a locale call.
    AlterEgo.builder().salt(VALID_SALT).build();
  }

  @Test
  void acceptsExplicitLocale() {
    AlterEgo.builder().salt(VALID_SALT).locale(Locale.US).build();
  }

  @Test
  void rejectsNullLocale() {
    assertThrows(NullPointerException.class, () -> AlterEgo.builder().locale(null));
  }

  @Test
  void rejectsNullNullPolicy() {
    assertThrows(NullPointerException.class, () -> AlterEgo.builder().nullPolicy(null));
  }

  @Test
  void acceptsExplicitUniqueMaxAttempts() {
    AlterEgo.builder().salt(VALID_SALT).uniqueMaxAttempts(1).build();
  }

  @Test
  void rejectsZeroUniqueMaxAttempts() {
    AlterEgoConfigException ex =
        assertThrows(
            AlterEgoConfigException.class,
            () -> AlterEgo.builder().salt(VALID_SALT).uniqueMaxAttempts(0).build());
    assertTrue(ex.getMessage().contains("uniqueMaxAttempts"));
  }

  @Test
  void rejectsNegativeUniqueMaxAttempts() {
    assertThrows(
        AlterEgoConfigException.class,
        () -> AlterEgo.builder().salt(VALID_SALT).uniqueMaxAttempts(-1).build());
  }

  @Test
  void acceptsExplicitRawMappingKeys() {
    AlterEgo.builder().salt(VALID_SALT).rawMappingKeys(true).build();
  }

  @Test
  void saltIsDefensivelyCopiedFromCallersArray() {
    byte[] mutableSalt = VALID_SALT.clone();
    AlterEgo built = AlterEgo.builder().salt(mutableSalt).build();
    AlterEgo reference = AlterEgo.builder().salt(VALID_SALT).build();

    // Mutate the caller's array *after* passing it to the builder. If the builder didn't
    // defensively copy, this would change the salt effectively used by `built`.
    mutableSalt[0] = (byte) (mutableSalt[0] + 1);

    Strategy<String> revealsRandomness = (in, ctx) -> String.valueOf(ctx.random().nextInt(1_000_000));
    String fromBuilt = built.bind("test:mutation-check", revealsRandomness).apply("x");
    String fromReference = reference.bind("test:mutation-check", revealsRandomness).apply("x");

    assertEquals(fromReference, fromBuilt);
  }
}
