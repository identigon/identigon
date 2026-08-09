package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class UtilityTransformationsTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).build();
  }

  // --- pattern() -------------------------------------------------------------------------------

  @Test
  void patternOutputMatchesShape() {
    Transformation<String> t = alterego().pattern("DLDDDL");
    String out = t.apply("some-input");
    assertTrue(Pattern.matches("[0-9][A-Z][0-9]{3}[A-Z]", out));
  }

  @Test
  void patternWithLiteralsPreservesThem() {
    Transformation<String> t = alterego().pattern("LLDD DLL");
    String out = t.apply("GU12 4XY");
    assertTrue(Pattern.matches("[A-Z]{2}[0-9]{2} [0-9][A-Z]{2}", out));
    assertEquals(' ', out.charAt(4));
  }

  @Test
  void patternIsDeterministic() {
    Transformation<String> t = alterego().pattern("DLDDDL");
    assertEquals(t.apply("x"), alterego().pattern("DLDDDL").apply("x"));
  }

  @Test
  void malformedPatternThrowsImmediatelyAtFactoryTime() {
    assertThrows(AlterEgoPatternException.class, () -> alterego().pattern("DL\\"));
  }

  @Test
  void differentPatternTextsUseDifferentDomains() {
    // Same six-digit shape, differing only by a trailing literal that consumes no randomness.
    // If both shared a domain, every one of the six digits would be identical for the same
    // input; confirm at least one input shows a difference.
    Transformation<String> withoutSuffix = alterego().pattern("DDDDDD");
    Transformation<String> withSuffix = alterego().pattern("DDDDDD!");

    boolean anyDifferent = false;
    for (int i = 0; i < 20; i++) {
      String a = withoutSuffix.apply("value-" + i);
      String b = withSuffix.apply("value-" + i).substring(0, 6);
      if (!a.equals(b)) {
        anyDifferent = true;
      }
    }
    assertTrue(anyDifferent);
  }

  @Test
  void sameTextPatternCallsShareOutputForSameInput() {
    // Calling pattern() twice with the identical text is the same "kind" of transformation and
    // should behave identically for the same input (same derived domain).
    assertEquals(alterego().pattern("DLDDDL").apply("x"), alterego().pattern("DLDDDL").apply("x"));
  }

  @Test
  void patternAlwaysMatchesShapeAcrossManyInputs() {
    Transformation<String> t = alterego().pattern("DLDDDL");
    for (String input : manyInputs()) {
      assertTrue(Pattern.matches("[0-9][A-Z][0-9]{3}[A-Z]", t.apply(input)), "input=" + input);
    }
  }

  // --- constant() ------------------------------------------------------------------------------

  @Test
  void constantAlwaysReturnsTheFixedStringValue() {
    Transformation<String> t = alterego().constant("REDACTED");
    assertEquals("REDACTED", t.apply("anything"));
    assertEquals("REDACTED", t.apply("something-else"));
  }

  @Test
  void constantWorksForNonStringSupportedTypes() {
    LocalDate fixed = LocalDate.of(1900, 1, 1);
    Transformation<LocalDate> t = alterego().constant(fixed);
    assertEquals(fixed, t.apply(LocalDate.of(2026, 7, 13)));
  }

  @Test
  void constantWorksForEnumValues() {
    Transformation<UkNation> t = alterego().constant(UkNation.ENGLAND);
    assertEquals(UkNation.ENGLAND, t.apply(UkNation.SCOTLAND));
  }

  // --- mask() ----------------------------------------------------------------------------------

  @Test
  void fullMaskReplacesEveryCharacter() {
    Transformation<String> t = alterego().mask('*');
    assertEquals("****************", t.apply("credit-card-1234"));
    assertEquals("", t.apply(""));
  }

  @Test
  void maskReplacesAllButLastNCharacters() {
    Transformation<String> t = alterego().mask('*', 4);
    assertEquals("************1234", t.apply("credit-card-1234"));
  }

  @Test
  void maskLeavesShortInputUnchanged() {
    Transformation<String> t = alterego().mask('*', 10);
    assertEquals("short", t.apply("short"));
  }

  @Test
  void maskLeavesExactLengthInputUnchanged() {
    Transformation<String> t = alterego().mask('*', 5);
    assertEquals("exact", t.apply("exact"));
  }

  @Test
  void maskRejectsNegativeKeepLast() {
    assertThrows(AlterEgoConfigException.class, () -> alterego().mask('*', -1));
  }

  @Test
  void maskAlwaysPreservesLength() {
    for (String input : manyInputs()) {
      for (int keepLast = 0; keepLast <= 30; keepLast++) {
        Transformation<String> t = alterego().mask('*', keepLast);
        assertEquals(
            input.length(), t.apply(input).length(), "input=" + input + " keepLast=" + keepLast);
      }
    }
  }

  // --- redact() --------------------------------------------------------------------------------

  @Test
  void redactReturnsSensibleConstants() {
    assertEquals("", alterego().redact(String.class).apply("anything"));
    assertEquals(0, alterego().redact(Integer.class).apply(123));
    assertEquals(0L, alterego().redact(Long.class).apply(123L));
    assertEquals(Boolean.FALSE, alterego().redact(Boolean.class).apply(true));
    assertEquals(LocalDate.of(1970, 1, 1), alterego().redact(LocalDate.class).apply(LocalDate.of(2026, 7, 13)));
    assertEquals(java.time.LocalDateTime.of(1970, 1, 1, 0, 0), alterego().redact(java.time.LocalDateTime.class).apply(java.time.LocalDateTime.of(2026, 7, 13, 12, 0)));
    assertEquals(java.time.Instant.EPOCH, alterego().redact(java.time.Instant.class).apply(java.time.Instant.EPOCH.plusSeconds(10)));
    assertEquals(new java.util.UUID(0L, 0L), alterego().redact(java.util.UUID.class).apply(new java.util.UUID(1L, 2L)));
  }

  @Test
  void redactThrowsForUnsupportedTypes() {
    assertThrows(AlterEgoConfigException.class, () -> alterego().redact(UkNation.class));
  }

  /** A varied fixed input set (including the empty string and non-ASCII) for the loop-based tests. */
  private static List<String> manyInputs() {
    List<String> inputs =
        new ArrayList<>(
            List.of(
                "", "a", "Z", "7", "!", "  ", "abc", "café", "naïve", "東京都",
                "🙂", "MiXeD", "the-quick-brown-fox-jumps!!", "1234567890123456789012345678"));
    for (int i = 0; i < 200; i++) {
      inputs.add("input-" + i);
    }
    return inputs;
  }
}
