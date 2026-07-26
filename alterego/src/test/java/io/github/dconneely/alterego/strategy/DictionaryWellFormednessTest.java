package io.github.dconneely.alterego.strategy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dconneely.alterego.AlterEgoConfigException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DictionaryWellFormednessTest {

  private static final String VALID_HEADER =
      """
      # source: Test Org: Test Dataset
      # data-url: https://example.invalid/data
      # licence: TEST-LICENCE
      # licence-url: https://example.invalid/licence
      # retrieved: 2026-07-14
      """;

  @Test
  void fixtureDictionariesArePresentAndWellFormed() {
    Dictionary firstNames = DictionaryLoader.load("ZZ", "first-names");
    assertDoesNotThrow(
        () -> DictionaryWellFormedness.validate(firstNames, "ZZ/first-names", DictionaryLoader.licenceTextExists(firstNames.header().licence())));

    Dictionary towns = DictionaryLoader.load("ZZ", "towns");
    assertDoesNotThrow(
        () -> DictionaryWellFormedness.validate(towns, "ZZ/towns", DictionaryLoader.licenceTextExists(towns.header().licence())));
    assertDoesNotThrow(() -> DictionaryWellFormedness.validateTownTags(towns, "ZZ/towns"));

    Dictionary orgComponents = DictionaryLoader.load("ZZ", "organisation-components");
    assertDoesNotThrow(
        () ->
            DictionaryWellFormedness.validate(
                orgComponents,
                "ZZ/organisation-components",
                DictionaryLoader.licenceTextExists(orgComponents.header().licence())));
    assertDoesNotThrow(
        () -> DictionaryWellFormedness.validateOrgComponentTags(orgComponents, "ZZ/organisation-components"));

    Dictionary phoneRanges = DictionaryLoader.load("ZZ", "phone-ranges");
    assertDoesNotThrow(
        () ->
            DictionaryWellFormedness.validate(
                phoneRanges, "ZZ/phone-ranges", DictionaryLoader.licenceTextExists(phoneRanges.header().licence())));
    assertDoesNotThrow(() -> DictionaryWellFormedness.validatePhoneRangeTags(phoneRanges, "ZZ/phone-ranges"));
  }

  @Test
  void realGbDictionariesAreWellFormed() {
    // The build-time regression check for the actual shipped UK data: catches any future
    // accidental corruption of a committed dictionary file (bad sort, duplicate, missing
    // header, orphaned licence reference) before it reaches a release.
    for (String name :
        List.of(
            "surnames",
            "first-names",
            "towns",
            "street-themes",
            "street-types",
            "organisation-components",
            "phone-ranges")) {
      Dictionary dict = DictionaryLoader.load("GB", name);
      boolean licenceOk = DictionaryLoader.licenceTextExists(dict.header().licence());
      assertDoesNotThrow(
          () -> DictionaryWellFormedness.validate(dict, "GB/" + name, licenceOk),
          "GB/" + name + " failed well-formedness");
    }
    Dictionary towns = DictionaryLoader.load("GB", "towns");
    assertDoesNotThrow(() -> DictionaryWellFormedness.validateTownTags(towns, "GB/towns"));
    Dictionary orgComponents = DictionaryLoader.load("GB", "organisation-components");
    assertDoesNotThrow(
        () -> DictionaryWellFormedness.validateOrgComponentTags(orgComponents, "GB/organisation-components"));
    Dictionary phoneRanges = DictionaryLoader.load("GB", "phone-ranges");
    assertDoesNotThrow(() -> DictionaryWellFormedness.validatePhoneRangeTags(phoneRanges, "GB/phone-ranges"));
  }

  @Test
  void emptyDictionaryFails() {
    Dictionary empty = DictionaryParser.parse(VALID_HEADER, "test");
    AlterEgoConfigException ex =
        assertThrows(AlterEgoConfigException.class, () -> DictionaryWellFormedness.validate(empty, "test", true));
    assertTrue(ex.getMessage().contains("empty"));
  }

  @Test
  void duplicateEntryFails() {
    Dictionary duplicated = DictionaryParser.parse(VALID_HEADER + "Alice\nAlice\nBob\n", "test");
    AlterEgoConfigException ex =
        assertThrows(
            AlterEgoConfigException.class, () -> DictionaryWellFormedness.validate(duplicated, "test", true));
    assertTrue(ex.getMessage().contains("duplicate"));
  }

  @Test
  void sameValueWithDifferentTagsIsNotADuplicate() {
    // A tagged dictionary may legitimately repeat a value under different tags (e.g. London
    // spans several UK postcode areas) — only an exact repeated (value, tags) row is rejected.
    Dictionary multiAreaTown =
        DictionaryParser.parse(
            VALID_HEADER + "London\tE\tENGLAND\nLondon\tSW\tENGLAND\nLondon\tWC\tENGLAND\n", "test");
    assertDoesNotThrow(() -> DictionaryWellFormedness.validate(multiAreaTown, "test", true));
    assertEquals(3, multiAreaTown.entries().size());
  }

  @Test
  void exactDuplicateRowStillFails() {
    Dictionary exactDuplicateRow =
        DictionaryParser.parse(VALID_HEADER + "London\tE\tENGLAND\nLondon\tE\tENGLAND\n", "test");
    assertThrows(
        AlterEgoConfigException.class,
        () -> DictionaryWellFormedness.validate(exactDuplicateRow, "test", true));
  }

  @Test
  void unsortedEntriesFail() {
    Dictionary unsorted = DictionaryParser.parse(VALID_HEADER + "Bob\nAlice\n", "test");
    AlterEgoConfigException ex =
        assertThrows(
            AlterEgoConfigException.class, () -> DictionaryWellFormedness.validate(unsorted, "test", true));
    assertTrue(ex.getMessage().contains("sorted"));
  }

  @Test
  void missingLicenceTextFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "Alice\n", "test");
    AlterEgoConfigException ex =
        assertThrows(
            AlterEgoConfigException.class, () -> DictionaryWellFormedness.validate(dict, "test", false));
    assertTrue(ex.getMessage().contains("LICENCES"));
  }

  @Test
  void townTagWrongCountFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "Manchester\tM\n", "test");
    assertThrows(
        AlterEgoConfigException.class, () -> DictionaryWellFormedness.validateTownTags(dict, "test"));
  }

  @Test
  void townTagInvalidPostcodeAreaFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "Manchester\tManc\tENGLAND\n", "test");
    assertThrows(
        AlterEgoConfigException.class, () -> DictionaryWellFormedness.validateTownTags(dict, "test"));
  }

  @Test
  void townTagInvalidCountryFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "Manchester\tM\tATLANTIS\n", "test");
    assertThrows(
        AlterEgoConfigException.class, () -> DictionaryWellFormedness.validateTownTags(dict, "test"));
  }

  @Test
  void orgComponentTagWrongCountFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "Trading\n", "test");
    assertThrows(
        AlterEgoConfigException.class,
        () -> DictionaryWellFormedness.validateOrgComponentTags(dict, "test"));
  }

  @Test
  void orgComponentTagInvalidValueFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "Trading\tADJECTIVE\n", "test");
    assertThrows(
        AlterEgoConfigException.class,
        () -> DictionaryWellFormedness.validateOrgComponentTags(dict, "test"));
  }

  @Test
  void phoneRangeTagWrongCountFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "01234560\n", "test");
    assertThrows(
        AlterEgoConfigException.class, () -> DictionaryWellFormedness.validatePhoneRangeTags(dict, "test"));
  }

  @Test
  void phoneRangeValueNotEightDigitsFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "0123456\t012 345 6XXX\tLS\n", "test");
    assertThrows(
        AlterEgoConfigException.class, () -> DictionaryWellFormedness.validatePhoneRangeTags(dict, "test"));
  }

  @Test
  void phoneRangeTemplateMissingXxxSuffixFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "01234560\t0123 4560\tLS\n", "test");
    assertThrows(
        AlterEgoConfigException.class, () -> DictionaryWellFormedness.validatePhoneRangeTags(dict, "test"));
  }

  @Test
  void phoneRangeTemplateNotReconstructingValueFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "01234560\t0999 999 0XXX\tLS\n", "test");
    assertThrows(
        AlterEgoConfigException.class, () -> DictionaryWellFormedness.validatePhoneRangeTags(dict, "test"));
  }

  @Test
  void phoneRangeInvalidPlaceTagFails() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "01234560\t0123 456 0XXX\tLondon\n", "test");
    assertThrows(
        AlterEgoConfigException.class, () -> DictionaryWellFormedness.validatePhoneRangeTags(dict, "test"));
  }

  @Test
  void phoneRangeAcceptsNoneAndMobilePlaceTags() {
    Dictionary dict =
        DictionaryParser.parse(
            VALID_HEADER + "01234560\t0123 456 0XXX\tNONE\n09876540\t0987 654 0XXX\tMOBILE\n", "test");
    assertDoesNotThrow(() -> DictionaryWellFormedness.validatePhoneRangeTags(dict, "test"));
  }
}
