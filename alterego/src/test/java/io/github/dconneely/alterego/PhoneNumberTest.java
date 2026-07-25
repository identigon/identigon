package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code phoneNumber()}: the section 4.1 fictionality guarantee against the GB Ofcom
 * drama ranges ({@code docs/phone-ranges.md}), in-place digit replacement with punctuation
 * preserved (the {@code PhoneOptions.realistic()} and no-range-country paths, where the output
 * takes the input's own shape rather than a reserved template's), and the {@code realistic()}
 * opt-out.
 */
class PhoneNumberTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  // The 8-digit fixed prefix of every range in src/main/resources/dictionaries/GB/phone-ranges.txt.
  private static final Set<String> GB_FIXED_PREFIXES =
      Set.of(
          "01134960", "01144960", "01154960", "01164960", "01174960", "01184960", "01214960",
          "01314960", "01414960", "01514960", "01614960", "01632960", "01914980", "02079460",
          "02896496", "02920180", "07700900");

  private static AlterEgo gb() {
    return AlterEgo.builder().salt(SALT).build();
  }

  // The fictionality property test (every default GB output falls inside a published range,
  // large sample) now lives in FictionalityTest, alongside postcode()'s and emailAddress()'s.

  @Test
  void realisticOptionPreservesInputsPunctuationAndReplacesDigitsInPlace() {
    Transformation<String> t = gb().phoneNumber(PhoneOptions.realistic());
    String input = "+44 (0)20-7946 0958";
    String result = t.apply(input);
    assertEquals(input.length(), result.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c >= '0' && c <= '9') {
        assertTrue(Character.isDigit(result.charAt(i)), "expected a digit at index " + i);
      } else {
        assertEquals(c, result.charAt(i), "punctuation mismatch at index " + i);
      }
    }
  }

  @Test
  void realisticOptionCanProduceOutputOutsideThePublishedRanges() {
    // Not a guarantee either way, mirroring PostcodeTest's realistic() test: across enough
    // samples, at least one output's fixed 8-digit prefix should fall outside the published set.
    Transformation<String> t = gb().phoneNumber(PhoneOptions.realistic());
    boolean sawNonMatching = false;
    for (int i = 0; i < 200; i++) {
      String result = t.apply("020 7946 0958");
      String digitsOnly = result.replaceAll("[^0-9]", "");
      if (digitsOnly.length() < 8 || !GB_FIXED_PREFIXES.contains(digitsOnly.substring(0, 8))) {
        sawNonMatching = true;
        break;
      }
    }
    assertTrue(sawNonMatching, "realistic() never produced an output outside the published ranges");
  }

  @Test
  void noRangeCountryFallsBackToPlainDigitReplacementWithoutThrowing() {
    AlterEgo us = AlterEgo.builder().salt(SALT).locale(Locale.US).build();
    Transformation<String> t = us.phoneNumber();
    String input = "(555) 123-4567";
    String result = t.apply(input);
    assertEquals(input.length(), result.length());
    assertTrue(Character.isDigit(result.charAt(1)));
    assertEquals('(', result.charAt(0));
    assertEquals(')', result.charAt(4));
  }

  @Test
  void isDeterministic() {
    Transformation<String> t = gb().phoneNumber();
    assertEquals(t.apply("020 7946 0958"), gb().phoneNumber().apply("020 7946 0958"));
  }

  @Test
  void missingCountryFailsFastAtFactoryCallTime() {
    AlterEgo noCountry = AlterEgo.builder().salt(SALT).locale(Locale.of("en")).build();
    assertThrows(AlterEgoConfigException.class, noCountry::phoneNumber);
  }

  @Test
  void nonAsciiAndEmptyInputsAreHandled() {
    Transformation<String> t = gb().phoneNumber(PhoneOptions.realistic());
    assertEquals("", t.apply(""));
    String result = t.apply("Örebro 020-7946-0958");
    assertEquals("Örebro 020-7946-0958".length(), result.length());
  }
}
