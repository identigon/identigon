package org.identigon.alterego.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import org.identigon.alterego.AlterEgoConfigException;
import org.junit.jupiter.api.Test;

class DictionaryLoaderTest {

  @Test
  void loadsFixtureDictionary() {
    Dictionary dict = DictionaryLoader.load("ZZ", "first-names");
    assertEquals(List.of("Alice", "Bob", "Carol", "Dave", "Eve"), dict.values());
  }

  @Test
  void loadsTaggedFixtureDictionary() {
    Dictionary dict = DictionaryLoader.load("ZZ", "towns");
    assertEquals(3, dict.entries().size());
  }

  @Test
  void cachesAcrossCalls() {
    Dictionary first = DictionaryLoader.load("ZZ", "first-names");
    Dictionary second = DictionaryLoader.load("ZZ", "first-names");
    assertSame(first, second);
  }

  @Test
  void missingCountryThrowsNamingCountryAndDictionary() {
    AlterEgoConfigException ex =
        assertThrows(AlterEgoConfigException.class, () -> DictionaryLoader.load("XX", "first-names"));
    assertTrue(ex.getMessage().contains("XX"));
    assertTrue(ex.getMessage().contains("first-names"));
  }

  @Test
  void missingDictionaryNameThrows() {
    assertThrows(AlterEgoConfigException.class, () -> DictionaryLoader.load("ZZ", "no-such-dictionary"));
  }

  @Test
  void requireCountryReturnsIsoCountry() {
    assertEquals("GB", DictionaryLoader.requireCountry(Locale.UK));
  }

  @Test
  void requireCountryThrowsForLocaleWithNoCountry() {
    assertThrows(AlterEgoConfigException.class, () -> DictionaryLoader.requireCountry(Locale.of("en")));
  }

  @Test
  void licenceTextExistsForFixtureLicence() {
    assertTrue(DictionaryLoader.licenceTextExists("TEST-LICENCE"));
  }

  @Test
  void licenceTextDoesNotExistForUnknownLicence() {
    assertFalse(DictionaryLoader.licenceTextExists("NO-SUCH-LICENCE"));
  }
}
