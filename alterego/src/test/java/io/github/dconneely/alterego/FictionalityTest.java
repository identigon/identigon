package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The section 4.1 "fictional by default" property tests, gathered in one place now that there
 * are three built-ins with a reserved/impossible value space (spec section 10): every default
 * {@code postcode()} inward code violates the real inward-code rule, every default
 * {@code emailAddress()} uses an RFC 2606 reserved domain, and every default {@code
 * phoneNumber()} falls inside a published Ofcom drama range — each over a large sample.
 */
class FictionalityTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final int SAMPLE_SIZE = 500;

  private static final Set<Character> NEVER_USED_POSTCODE_LETTERS = Set.of('C', 'I', 'K', 'M', 'O', 'V');
  private static final Set<String> RESERVED_EMAIL_DOMAINS = Set.of("example.com", "example.net", "example.org");
  private static final Set<String> GB_PHONE_FIXED_PREFIXES =
      Set.of(
          "01134960", "01144960", "01154960", "01164960", "01174960", "01184960", "01214960",
          "01314960", "01414960", "01514960", "01614960", "01632960", "01914980", "02079460",
          "02896496", "02920180", "07700900");

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).build();
  }

  @Test
  void everyDefaultPostcodeViolatesTheInwardCodeRule() {
    Transformation<String> t = alterego().postcode();
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      String result = t.apply("input-" + i);
      char lastLetter = result.charAt(result.length() - 1);
      assertTrue(
          NEVER_USED_POSTCODE_LETTERS.contains(lastLetter),
          "expected a never-used letter, got '" + lastLetter + "' in " + result);
    }
  }

  @Test
  void everyDefaultEmailUsesAReservedDomain() {
    Transformation<String> t = alterego().emailAddress();
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      String result = t.apply("user" + i + "@realmail.example-real.com");
      String domain = result.substring(result.lastIndexOf('@') + 1);
      assertTrue(RESERVED_EMAIL_DOMAINS.contains(domain), "not a reserved domain: " + domain);
    }
  }

  @Test
  void everyDefaultPhoneNumberFallsInsideAPublishedRange() {
    Transformation<String> t = alterego().phoneNumber();
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      String result = t.apply("input-" + i);
      String digitsOnly = result.replaceAll("[^0-9]", "");
      assertEquals(11, digitsOnly.length(), "unexpected digit count in output: " + result);
      assertTrue(
          GB_PHONE_FIXED_PREFIXES.contains(digitsOnly.substring(0, 8)),
          "not a published fixed prefix: " + digitsOnly.substring(0, 8) + " in " + result);
    }
  }
}
