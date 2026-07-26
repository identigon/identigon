package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Exercises firstName()/lastName()/fullName() against the ZZ synthetic test fixtures (for
 * exact, controlled assertions) and the default-configured GB dictionaries (for end-to-end
 * coverage of the default-configured path). firstName() draws from a real curated dictionary;
 * lastName() draws from an authored, deliberately fictional dictionary (ADR 0010) — see
 * {@link FictionalityTest} for the property test confirming every lastName() output is drawn
 * from that fictional set.
 */
class NameTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final Locale ZZ = Locale.of("en", "ZZ");
  private static final List<String> FIXTURE_FIRST_NAMES = List.of("Alice", "Bob", "Carol", "Dave", "Eve");
  private static final List<String> FIXTURE_SURNAMES =
      List.of("Anderson", "Baker", "Clarke", "Dixon", "Ellis");

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).locale(ZZ).build();
  }

  // --- firstName() / lastName() ---------------------------------------------------------------

  @Test
  void firstNamePicksFromDictionary() {
    Transformation<String> t = alterego().firstName();
    assertTrue(FIXTURE_FIRST_NAMES.contains(t.apply("original")));
  }

  @Test
  void firstNameIsDeterministic() {
    Transformation<String> t = alterego().firstName();
    assertEquals(t.apply("x"), alterego().firstName().apply("x"));
  }

  @Test
  void lastNamePicksFromItsOwnDictionaryNotFirstNames() {
    Transformation<String> t = alterego().lastName();
    assertTrue(FIXTURE_SURNAMES.contains(t.apply("x")));
  }

  @Test
  void missingCountryFailsFastAtFactoryCallTime() {
    AlterEgo noCountry = AlterEgo.builder().salt(SALT).locale(Locale.of("en")).build();
    assertThrows(AlterEgoConfigException.class, noCountry::firstName);
  }

  @Test
  void missingDictionaryForUnshippedCountryFailsFast() {
    // US is not shipped in v1 (spec section 4: "others, starting with US, are post-v1");
    // confirms fail-fast behaviour rather than a confusing failure deep inside apply().
    AlterEgo us = AlterEgo.builder().salt(SALT).locale(Locale.US).build();
    assertThrows(AlterEgoConfigException.class, us::firstName);
  }

  @Test
  void defaultLocaleGbFirstNamePicksFromRealDictionary() {
    // Default locale is Locale.UK; confirms the real curated dictionary (not a
    // synthetic fixture) loads and works via the default-configured AlterEgo instance.
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    String result = gb.firstName().apply("original");
    assertTrue(Character.isUpperCase(result.charAt(0)));
  }

  @Test
  void defaultLocaleGbLastNamePicksFromAuthoredFictionalDictionary() {
    // Default locale is Locale.UK; confirms the authored, deliberately fictional
    // dictionary (ADR 0010, not real population data) loads and works via the
    // default-configured AlterEgo instance.
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    String result = gb.lastName().apply("original");
    assertTrue(Character.isUpperCase(result.charAt(0)));
  }

  @Test
  void defaultLocaleGbFullNameAgreesWithStandaloneParts() {
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    String full = gb.fullName().apply("Original Person");
    String[] parts = full.split(" ", 2);
    assertEquals(gb.firstName().apply("Original"), parts[0]);
    assertEquals(gb.lastName().apply("Person"), parts[1]);
  }

  @Test
  void preserveInitialRestrictsToMatchingFirstLetter() {
    Transformation<String> t = alterego().firstName(NameOptions.preserveInitial());
    // Every fixture name starts with a distinct letter; feeding "Bxxx" should always yield "Bob".
    assertEquals("Bob", t.apply("Bxxx"));
  }

  @Test
  void preserveInitialFallsBackWhenNoMatch() {
    // No fixture name starts with 'Z'.
    Transformation<String> t = alterego().firstName(NameOptions.preserveInitial());
    assertTrue(FIXTURE_FIRST_NAMES.contains(t.apply("Zzz")));
  }

  // --- fullName() -------------------------------------------------------------------------------

  @Test
  void fullNameTwoTokensAgreesWithStandaloneFirstAndLastName() {
    // The M2 "done when" cross-consistency criterion: fullName("Alice Smith") parts equal
    // firstName("Alice") / lastName("Smith") applied separately.
    AlterEgo eg = alterego();
    String full = eg.fullName().apply("Original Person");
    String[] parts = full.split(" ", 2);

    String expectedFirst = eg.firstName().apply("Original");
    String expectedLast = eg.lastName().apply("Person");

    assertEquals(expectedFirst, parts[0]);
    assertEquals(expectedLast, parts[1]);
  }

  @Test
  void fullNameSingleTokenUsesSurnameDomain() {
    AlterEgo eg = alterego();
    String full = eg.fullName().apply("Cher");
    String expectedSurname = eg.lastName().apply("Cher");
    assertEquals(expectedSurname, full);
  }

  @Test
  void fullNameThreeTokensMiddleUsesFirstNameDomain() {
    AlterEgo eg = alterego();
    String full = eg.fullName().apply("Mary Jane Watson");
    String[] parts = full.split(" ");
    assertEquals(3, parts.length);
    assertEquals(eg.firstName().apply("Mary"), parts[0]);
    assertEquals(eg.firstName().apply("Jane"), parts[1]);
    assertEquals(eg.lastName().apply("Watson"), parts[2]);
  }

  @Test
  void fullNameHyphenatedSurnameIsRejoinedWithHyphen() {
    AlterEgo eg = alterego();
    String full = eg.fullName().apply("Alice Smith-Jones");
    assertTrue(full.contains("-"));
    String[] fullParts = full.split(" ", 2);
    String[] surnameSegments = fullParts[1].split("-");
    assertEquals(2, surnameSegments.length);
  }

  @Test
  void fullNameBlankInputReturnedUnchanged() {
    AlterEgo eg = alterego();
    assertEquals("", eg.fullName().apply(""));
    assertEquals("   ", eg.fullName().apply("   "));
  }

  @Test
  void fullNameIsDeterministic() {
    AlterEgo eg = alterego();
    assertEquals(eg.fullName().apply("Original Person"), alterego().fullName().apply("Original Person"));
  }
}
