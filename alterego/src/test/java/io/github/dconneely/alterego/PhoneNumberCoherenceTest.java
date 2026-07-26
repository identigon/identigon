package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** {@code phoneNumber()}'s record-coherence behaviour (SPECIFICATION.md section 6.3). */
class PhoneNumberCoherenceTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static AlterEgo gb() {
    return AlterEgo.builder().salt(SALT).build();
  }

  @Test
  void prefersAPlaceMatchingDramaRangeWhenTheAreaIsFixed() {
    // "E" (one of London's postcode areas) matches the 020 7946 0xxx range specifically.
    AlterEgo eg = gb();
    Transformation<String> phone = eg.phoneNumber();
    try (RecordScope rec = eg.record().with(AlterEgoAttributes.UK_POSTCODE_AREA, "E")) {
      for (int i = 0; i < 20; i++) {
        String result = rec.apply(phone, "input-" + i);
        assertTrue(result.startsWith("020 7946 0"), "expected a London drama number: " + result);
      }
    }
  }

  @Test
  void agreesWithCityChosenInTheSameScope() {
    AlterEgo eg = gb();
    Transformation<String> city = eg.city();
    Transformation<String> phone = eg.phoneNumber();
    try (RecordScope rec = eg.record().with(AlterEgoAttributes.UK_POSTCODE_AREA, "LS")) {
      String town = rec.apply(city, "input"); // Leeds, the only LS town
      String result = rec.apply(phone, "input");
      assertTrue(result.startsWith("0113 496 0"), "expected a Leeds drama number to match " + town + ": " + result);
    }
  }

  @Test
  void phoneFirstEstablishesARealAreaThatCityLaterAgreesWith() {
    // No pre-seeding: phoneNumber() is the first field to touch the record's place. It must
    // establish a *real* town's area (from towns.txt), not an arbitrary one, so a later city()
    // call in the same scope always finds a match.
    AlterEgo eg = gb();
    Transformation<String> phone = eg.phoneNumber();
    Transformation<String> city = eg.city();
    Transformation<String> areaReader =
        eg.bind("test:reads-area", (in, ctx) -> ctx.record().get(AlterEgoAttributes.UK_POSTCODE_AREA).orElse(""));

    for (int i = 0; i < 20; i++) {
      try (RecordScope rec = eg.record()) {
        rec.apply(phone, "input-" + i);
        String establishedArea = rec.apply(areaReader, "ignored");
        assertTrue(!establishedArea.isEmpty(), "phoneNumber() should have established an area");

        String cityResult = rec.apply(city, "input-" + i);
        assertTrue(
            GbTownAreas.BY_TOWN.getOrDefault(cityResult, java.util.List.of()).contains(establishedArea),
            "city '" + cityResult + "' does not carry the established area '" + establishedArea + "'");
      }
    }
  }

  @Test
  void fallsBackToTheNeutralRangeWhenTheFixedAreaHasNoMatchingRange() {
    // "BD" (Bradford) is a real towns.txt area with no phone range of its own.
    AlterEgo eg = gb();
    Transformation<String> phone = eg.phoneNumber();
    try (RecordScope rec = eg.record().with(AlterEgoAttributes.UK_POSTCODE_AREA, "BD")) {
      for (int i = 0; i < 20; i++) {
        String result = rec.apply(phone, "input-" + i);
        assertTrue(result.startsWith("01632 960"), "expected the neutral fallback range: " + result);
      }
    }
  }

  @Test
  void everyInScopeOutputStillFallsInsideAPublishedRange() {
    // The fictionality guarantee (section 4.1) holds inside a scope too — a scope only changes
    // *which* range gets picked, never removes the guarantee itself.
    java.util.Set<String> fixedPrefixes =
        java.util.Set.of(
            "01134960", "01144960", "01154960", "01164960", "01174960", "01184960", "01214960",
            "01314960", "01414960", "01514960", "01614960", "01632960", "01914980", "02079460",
            "02896496", "02920180", "07700900");
    AlterEgo eg = gb();
    Transformation<String> phone = eg.phoneNumber();
    for (int i = 0; i < 200; i++) {
      try (RecordScope rec = eg.record()) {
        String result = rec.apply(phone, "input-" + i);
        String digitsOnly = result.replaceAll("[^0-9]", "");
        assertTrue(fixedPrefixes.contains(digitsOnly.substring(0, 8)), "not a published fixed prefix: " + result);
      }
    }
  }
}
