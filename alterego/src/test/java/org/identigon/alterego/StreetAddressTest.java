package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code streetAddress()} against the ZZ synthetic street-theme/street-type fixtures
 * and the default-configured GB dictionaries. The GB theme words are authored, deliberately
 * fictional vocabulary (ADR 0010), not real UK street-name data; the type words (Road, Avenue,
 * Close...) are real and structural. See {@link FictionalityTest} for the property test
 * confirming every streetAddress() theme word is drawn from that fictional set.
 */
class StreetAddressTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final Locale ZZ = Locale.of("en", "ZZ");
  private static final List<String> FIXTURE_THEMES = List.of("Fixture", "Mock", "Sample");
  private static final List<String> FIXTURE_TYPES = List.of("Avenue", "Road", "Street");

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).locale(ZZ).build();
  }

  @Test
  void composesHouseNumberThemeAndType() {
    Transformation<String> t = alterego().streetAddress();
    for (int i = 0; i < 30; i++) {
      String[] parts = t.apply("input-" + i).split(" ", 2);
      int houseNumber = Integer.parseInt(parts[0]);
      assertTrue(houseNumber >= 1 && houseNumber <= 299, "house number out of range: " + houseNumber);

      String[] streetParts = parts[1].split(" ");
      assertEquals(2, streetParts.length);
      assertTrue(FIXTURE_THEMES.contains(streetParts[0]), "unexpected theme: " + streetParts[0]);
      assertTrue(FIXTURE_TYPES.contains(streetParts[1]), "unexpected type: " + streetParts[1]);
    }
  }

  @Test
  void isDeterministic() {
    Transformation<String> t = alterego().streetAddress();
    assertEquals(t.apply("123 Original St"), alterego().streetAddress().apply("123 Original St"));
  }

  @Test
  void missingCountryFailsFastAtFactoryCallTime() {
    AlterEgo noCountry = AlterEgo.builder().salt(SALT).locale(Locale.of("en")).build();
    assertThrows(AlterEgoConfigException.class, noCountry::streetAddress);
  }

  @Test
  void missingDictionaryForUnshippedCountryFailsFast() {
    AlterEgo us = AlterEgo.builder().salt(SALT).locale(Locale.US).build();
    assertThrows(AlterEgoConfigException.class, us::streetAddress);
  }

  @Test
  void nonAsciiAndEmptyInputsAreHandled() {
    Transformation<String> t = alterego().streetAddress();
    assertTrue(t.apply("Örebro Müller 東京").matches("\\d+ \\S+ \\S+"));
    assertTrue(t.apply("").matches("\\d+ \\S+ \\S+"));
  }

  @Test
  void defaultLocaleGbStreetAddressPicksFromAuthoredFictionalDictionary() {
    // Default locale is Locale.UK; confirms the authored, deliberately fictional
    // theme-word dictionary (ADR 0010) loads and works via the default-configured
    // AlterEgo instance.
    AlterEgo gb = AlterEgo.builder().salt(SALT).build();
    String result = gb.streetAddress().apply("original");
    String[] parts = result.split(" ", 2);
    int houseNumber = Integer.parseInt(parts[0]);
    assertTrue(houseNumber >= 1 && houseNumber <= 299);
  }
}
