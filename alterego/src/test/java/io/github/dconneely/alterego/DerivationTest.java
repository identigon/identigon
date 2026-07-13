package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class DerivationTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  @Test
  void sameInputsProduceSameKey() {
    byte[] a = Derivation.deriveKey(SALT, Derivation.PURPOSE_RANDOM, "alterego:first-name", "Alice", 0);
    byte[] b = Derivation.deriveKey(SALT, Derivation.PURPOSE_RANDOM, "alterego:first-name", "Alice", 0);
    assertArrayEquals(a, b);
  }

  @Test
  void keyIs32Bytes() {
    byte[] key = Derivation.deriveKey(SALT, Derivation.PURPOSE_RANDOM, "alterego:first-name", "Alice", 0);
    assertEquals(32, key.length);
  }

  @Test
  void differentPurposesProduceDifferentKeys() {
    byte[] random = Derivation.deriveKey(SALT, Derivation.PURPOSE_RANDOM, "d", "x", 0);
    byte[] mapkey = Derivation.deriveKey(SALT, Derivation.PURPOSE_MAPKEY, "d", "x", 0);
    byte[] record = Derivation.deriveKey(SALT, Derivation.PURPOSE_RECORD, "d", "x", 0);
    assertNotEquals(HexFormat.of().formatHex(random), HexFormat.of().formatHex(mapkey));
    assertNotEquals(HexFormat.of().formatHex(random), HexFormat.of().formatHex(record));
    assertNotEquals(HexFormat.of().formatHex(mapkey), HexFormat.of().formatHex(record));
  }

  @Test
  void differentCountersProduceDifferentKeys() {
    byte[] c0 = Derivation.deriveKey(SALT, Derivation.PURPOSE_RANDOM, "d", "x", 0);
    byte[] c1 = Derivation.deriveKey(SALT, Derivation.PURPOSE_RANDOM, "d", "x", 1);
    assertFalse(java.util.Arrays.equals(c0, c1));
  }

  @Test
  void nulSeparationPreventsAmbiguousBoundaries() {
    // "ab" + "c" and "a" + "bc" must not collide despite concatenating to the same bytes,
    // because each field is NUL-terminated in the message (Appendix A.1).
    byte[] domainAbC = Derivation.deriveKey(SALT, Derivation.PURPOSE_RANDOM, "ab", "c", 0);
    byte[] domainABc = Derivation.deriveKey(SALT, Derivation.PURPOSE_RANDOM, "a", "bc", 0);
    assertFalse(java.util.Arrays.equals(domainAbC, domainABc));
  }

  @Test
  void charSaltMatchesUtf8OfEquivalentBytes() {
    char[] chars = "hello-salt-value".toCharArray();
    byte[] expected = "hello-salt-value".getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(expected, Derivation.charsToUtf8(chars));
  }

  @Test
  void charSaltHandlesNonAscii() {
    char[] chars = "sël-café-🔑".toCharArray();
    byte[] expected = "sël-café-🔑".getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(expected, Derivation.charsToUtf8(chars));
  }
}
