package io.github.dconneely.alterego;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Appendix A.1: {@code message = utf8(purpose) || 0x00 || utf8(domain) || 0x00 ||
 * utf8(canonical) || 0x00 || uint32_be(counter)}, {@code key = HMAC-SHA256(salt, message)}.
 */
final class Derivation {

  static final String PURPOSE_RANDOM = "alterego/1/random";
  static final String PURPOSE_MAPKEY = "alterego/1/mapkey";
  static final String PURPOSE_RECORD = "alterego/1/record";

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private Derivation() {}

  /** Derives the 32-byte key for one (salt, purpose, domain, canonical, counter) tuple. */
  static byte[] deriveKey(byte[] salt, String purpose, String domain, String canonical, int counter) {
    return hmac(salt, buildMessage(purpose, domain, canonical, counter));
  }

  /** Builds a {@link HmacRandomness} stream directly from a randomness-purpose derivation. */
  static Randomness randomness(byte[] salt, String domain, String canonical, int counter) {
    return new HmacRandomness(deriveKey(salt, PURPOSE_RANDOM, domain, canonical, counter));
  }

  /**
   * The store key for {@code canonical} under {@code domain} (section 5.1, Appendix A.4):
   * {@code raw} writes the canonical text itself (the {@code rawMappingKeys} opt-in, section
   * 2.6); otherwise the purpose-separated {@code HMAC(salt, input)}, as 64 lowercase hex
   * characters. The single path both {@code DefaultMappings} and the {@code stored()}/
   * {@code unique()} decorator logic use, so the two never diverge.
   */
  static String mapKey(byte[] salt, String domain, String canonical, boolean raw) {
    if (raw) {
      return canonical;
    }
    byte[] key = deriveKey(salt, PURPOSE_MAPKEY, domain, canonical, 0);
    return HexFormat.of().formatHex(key);
  }

  private static byte[] buildMessage(String purpose, String domain, String canonical, int counter) {
    byte[] purposeBytes = purpose.getBytes(StandardCharsets.UTF_8);
    byte[] domainBytes = domain.getBytes(StandardCharsets.UTF_8);
    byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer =
        ByteBuffer.allocate(
            purposeBytes.length + 1 + domainBytes.length + 1 + canonicalBytes.length + 1 + 4);
    buffer
        .put(purposeBytes)
        .put((byte) 0)
        .put(domainBytes)
        .put((byte) 0)
        .put(canonicalBytes)
        .put((byte) 0)
        .putInt(counter);
    return buffer.array();
  }

  /** HMAC-SHA256(key, message); the single primitive Appendix A.1 and A.2 both build on. */
  static byte[] hmac(byte[] key, byte[] message) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
      return mac.doFinal(message);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(HMAC_ALGORITHM + " unavailable", e);
    }
  }

  /** Converts a {@code char[]} salt to bytes via UTF-8 without materialising a {@code String}. */
  static byte[] charsToUtf8(char[] chars) {
    ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
    byte[] bytes = new byte[encoded.remaining()];
    encoded.get(bytes);
    return bytes;
  }
}
