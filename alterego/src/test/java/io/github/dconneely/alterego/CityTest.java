package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code city()} against the ZZ synthetic town fixture and the real curated UK
 * dictionary.
 */
class CityTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final Locale ZZ = Locale.of("en", "ZZ");
  private static final List<String> FIXTURE_TOWNS = List.of("Fixtureburgh", "Sampletown", "Testford");

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).locale(ZZ).build();
  }

  @Test
  void picksFromDictionary() {
    Transformation<String> t = alterego().city();
    assertTrue(FIXTURE_TOWNS.contains(t.apply("original")));
  }

  @Test
  void isDeterministic() {
    Transformation<String> t = alterego().city();
    assertEquals(t.apply("x"), alterego().city().apply("x"));
  }

  @Test
  void missingCountryFailsFastAtFactoryCallTime() {
    AlterEgo noCountry = AlterEgo.builder().salt(SALT).locale(Locale.of("en")).build();
    assertThrows(AlterEgoConfigException.class, noCountry::city);
  }

  @Test
  void missingDictionaryForUnshippedCountryFailsFast() {
    AlterEgo us = AlterEgo.builder().salt(SALT).locale(Locale.US).build();
    assertThrows(AlterEgoConfigException.class, us::city);
  }

  @Test
  void nonAsciiAndEmptyInputsAreHandled() {
    Transformation<String> t = alterego().city();
    assertTrue(FIXTURE_TOWNS.contains(t.apply("Örebro Müller 東京")));
    assertTrue(FIXTURE_TOWNS.contains(t.apply("")));
  }

  @Test
  void defaultLocaleGbCityPicksFromRealDictionary() {
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    String result = gb.city().apply("original");
    assertTrue(Character.isUpperCase(result.charAt(0)));
  }
}
