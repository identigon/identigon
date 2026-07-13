package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Pattern;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

class AlterEgoUtilityTransformationsTest {

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

  @Property
  void patternAlwaysMatchesShapeAcrossManyInputs(@ForAll @StringLength(min = 1, max = 20) String input) {
    Transformation<String> t = alterego().pattern("DLDDDL");
    assertTrue(Pattern.matches("[0-9][A-Z][0-9]{3}[A-Z]", t.apply(input)));
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
    Transformation<GbCountry> t = alterego().constant(GbCountry.ENGLAND);
    assertEquals(GbCountry.ENGLAND, t.apply(GbCountry.SCOTLAND));
  }

  // --- mask() ----------------------------------------------------------------------------------

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

  @Property
  void maskAlwaysPreservesLength(
      @ForAll @StringLength(min = 0, max = 30) String input, @ForAll @IntRange(min = 0, max = 30) int keepLast) {
    Transformation<String> t = alterego().mask('*', keepLast);
    assertEquals(input.length(), t.apply(input).length());
  }
}
