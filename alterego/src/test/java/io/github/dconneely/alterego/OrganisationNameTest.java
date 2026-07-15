package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code organisationName()} against the ZZ synthetic test fixtures (for exact,
 * controlled assertions of the composition rule) and the real curated dictionary and legal
 * suffixes (for end-to-end coverage of the default-configured path).
 */
class OrganisationNameTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final Locale ZZ = Locale.of("en", "ZZ");
  private static final Set<String> FIXTURE_MODIFIERS = Set.of("Fixtureland", "Sampleshire");
  private static final Set<String> FIXTURE_NOUNS = Set.of("Mocking", "Stubbing", "Testing", "Widgets");

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).locale(ZZ).build();
  }

  @Test
  void composesThreeDistinctWordsFromFixture() {
    Transformation<String> t = alterego().organisationName();
    for (int i = 0; i < 30; i++) {
      String[] words = t.apply("input-" + i).split(" ");
      assertEquals(3, words.length);
      assertEquals(
          3, new HashSet<>(List.of(words)).size(), "words must be distinct: " + String.join(" ", words));
      for (String word : words) {
        assertTrue(
            FIXTURE_MODIFIERS.contains(word) || FIXTURE_NOUNS.contains(word),
            "unexpected word: " + word);
      }
    }
  }

  @Test
  void secondAndThirdWordsAreNeverModifiers() {
    // Structural guarantee, not a probabilistic one: position 2/3 draw from NOUN only.
    Transformation<String> t = alterego().organisationName();
    for (int i = 0; i < 30; i++) {
      String[] words = t.apply("input-" + i).split(" ");
      assertTrue(FIXTURE_NOUNS.contains(words[1]), "word 2 must be a NOUN: " + words[1]);
      assertTrue(FIXTURE_NOUNS.contains(words[2]), "word 3 must be a NOUN: " + words[2]);
    }
  }

  @Test
  void isDeterministic() {
    Transformation<String> t = alterego().organisationName();
    assertEquals(t.apply("Original Co"), alterego().organisationName().apply("Original Co"));
  }

  @Test
  void missingCountryFailsFastAtFactoryCallTime() {
    AlterEgo noCountry = AlterEgo.builder().salt(SALT).locale(Locale.of("en")).build();
    assertThrows(AlterEgoConfigException.class, noCountry::organisationName);
  }

  @Test
  void missingDictionaryForUnshippedCountryFailsFast() {
    AlterEgo us = AlterEgo.builder().salt(SALT).locale(Locale.US).build();
    assertThrows(AlterEgoConfigException.class, us::organisationName);
  }

  // --- Legal suffix preservation  --------------------------

  @Test
  void preservesEnglishLtdSuffix() {
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    String result = gb.organisationName().apply("Acme Trading Ltd");
    assertTrue(result.endsWith(" Ltd"), result);
  }

  @Test
  void preservesEnglishPlcSuffixCaseInsensitively() {
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    String result = gb.organisationName().apply("ACME TRADING PLC");
    assertTrue(result.endsWith(" plc"), result); // canonical casing from the suffix list, not the input's
  }

  @Test
  void preservesWelshSuffixes() {
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    assertTrue(gb.organisationName().apply("Acme Cyf.").endsWith(" Cyf."));
    assertTrue(gb.organisationName().apply("Acme c.c.c.").endsWith(" c.c.c."));
  }

  @Test
  void noSuffixDetectedMeansNoSuffixAppended() {
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    String result = gb.organisationName().apply("Acme Trading Partnership");
    List<String> recognised = List.of("Ltd", "plc", "Cyf.", "c.c.c.");
    for (String suffix : recognised) {
      assertFalse(result.endsWith(" " + suffix), result);
    }
  }

  @Test
  void defaultLocaleGbOrganisationNamePicksFromRealDictionary() {
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    String result = gb.organisationName().apply("original");
    assertEquals(3, result.split(" ").length);
  }

  @Test
  void nonAsciiAndEmptyInputsAreHandled() {
    Transformation<String> zz = alterego().organisationName();
    assertEquals(3, zz.apply("Örebro Müller 東京").split(" ").length);
    assertEquals(3, zz.apply("").split(" ").length);

    // Non-ASCII text preceding a recognised suffix still gets it detected and preserved.
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    assertTrue(gb.organisationName().apply("Örebro Müller 東京 Ltd").endsWith(" Ltd"));
  }
}
