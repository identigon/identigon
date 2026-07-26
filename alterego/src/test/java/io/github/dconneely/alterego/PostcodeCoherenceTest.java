package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@code postcode()}'s record-coherence behaviour (SPECIFICATION.md section 6.3). */
class PostcodeCoherenceTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final Set<Character> NEVER_USED_LETTERS = Set.of('C', 'I', 'K', 'M', 'O', 'V');

  private static AlterEgo gb() {
    return AlterEgo.builder().salt(SALT).build();
  }

  @Test
  void outwardCodeStartsWithTheFixedArea() {
    AlterEgo eg = gb();
    Transformation<String> postcode = eg.postcode();
    try (RecordScope rec = eg.record().with(AlterEgoAttributes.UK_POSTCODE_AREA, "LS")) {
      for (int i = 0; i < 20; i++) {
        String result = rec.apply(postcode, "input-" + i);
        assertTrue(result.startsWith("LS"), "expected outward code to start with LS: " + result);
      }
    }
  }

  @Test
  void agreesWithCityChosenInTheSameScope() {
    AlterEgo eg = gb();
    Transformation<String> city = eg.city();
    Transformation<String> postcode = eg.postcode();
    try (RecordScope rec = eg.record().with(AlterEgoAttributes.UK_POSTCODE_AREA, "LS")) {
      String town = rec.apply(city, "input");
      String result = rec.apply(postcode, "input");
      assertTrue(result.startsWith("LS"), "postcode should start with LS to match " + town);
    }
  }

  @Test
  void postcodeFirstEstablishesARealAreaThatCityLaterAgreesWith() {
    // No pre-seeding: postcode() is the first field to touch the record's place. It must
    // establish a *real* town's area (from towns.txt), not an arbitrary/fabricated one, so a
    // later city() call in the same scope always finds a match, and the outward code it built
    // for itself must start with that exact established area.
    AlterEgo eg = gb();
    Transformation<String> postcode = eg.postcode();
    Transformation<String> city = eg.city();
    Transformation<String> areaReader =
        eg.bind("test:reads-area", (in, ctx) -> ctx.record().get(AlterEgoAttributes.UK_POSTCODE_AREA).orElse(""));

    for (int i = 0; i < 20; i++) {
      try (RecordScope rec = eg.record()) {
        String postcodeResult = rec.apply(postcode, "input-" + i);
        String establishedArea = rec.apply(areaReader, "ignored");
        assertTrue(!establishedArea.isEmpty(), "postcode() should have established an area");
        assertTrue(
            postcodeResult.startsWith(establishedArea),
            "outward code '" + postcodeResult + "' should start with established area '" + establishedArea + "'");

        String cityResult = rec.apply(city, "input-" + i);
        assertTrue(
            GbTownAreas.BY_TOWN.getOrDefault(cityResult, java.util.List.of()).contains(establishedArea),
            "city '" + cityResult + "' does not carry the established area '" + establishedArea + "'");
      }
    }
  }

  @Test
  void impossibleInwardCodeGuaranteeStillHoldsInsideAScope() {
    AlterEgo eg = gb();
    Transformation<String> postcode = eg.postcode();
    try (RecordScope rec = eg.record().with(AlterEgoAttributes.UK_POSTCODE_AREA, "LS")) {
      for (int i = 0; i < 200; i++) {
        String result = rec.apply(postcode, "input-" + i);
        char lastLetter = result.charAt(result.length() - 1);
        assertTrue(NEVER_USED_LETTERS.contains(lastLetter), "expected a never-used letter in " + result);
      }
    }
  }
}
