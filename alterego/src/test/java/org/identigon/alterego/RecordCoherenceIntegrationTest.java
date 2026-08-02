package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The full spec section 10 record-coherence suite: town/postcode/phone agree whichever field
 * runs first, in every ordering — not just the city-first case each built-in's own coherence test
 * already covers individually.
 */
class RecordCoherenceIntegrationTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  // The 6 orderings of {city=0, postcode=1, phone=2}.
  private static final List<int[]> ORDERINGS =
      List.of(
          new int[] {0, 1, 2},
          new int[] {0, 2, 1},
          new int[] {1, 0, 2},
          new int[] {1, 2, 0},
          new int[] {2, 0, 1},
          new int[] {2, 1, 0});

  private static AlterEgo gb() {
    return AlterEgo.builder().salt(SALT).build();
  }

  @Test
  void townPostcodeAndPhoneAgreeInEveryFieldOrdering() {
    AlterEgo eg = gb();
    Transformation<String> city = eg.city();
    Transformation<String> postcode = eg.postcode();
    Transformation<String> phone = eg.phoneNumber();
    Transformation<String> areaReader =
        eg.bind("test:reads-area", (in, ctx) -> ctx.record().get(AlterEgoAttributes.UK_POSTCODE_AREA).orElse(""));

    int caseIndex = 0;
    for (int[] ordering : ORDERINGS) {
      String input = "input-" + caseIndex++;
      String[] resultByField = new String[3]; // [city, postcode, phone]
      try (RecordScope rec = eg.record()) {
        for (int field : ordering) {
          resultByField[field] =
              switch (field) {
                case 0 -> rec.apply(city, input);
                case 1 -> rec.apply(postcode, input);
                default -> rec.apply(phone, input);
              };
        }
        String area = rec.apply(areaReader, "ignored");

        assertTrue(!area.isEmpty(), "an area should have been established, ordering " + java.util.Arrays.toString(ordering));
        assertTrue(
            GbTownAreas.BY_TOWN.getOrDefault(resultByField[0], List.of()).contains(area),
            "city '" + resultByField[0] + "' inconsistent with area '" + area + "'");
        assertTrue(
            resultByField[1].startsWith(area),
            "postcode '" + resultByField[1] + "' inconsistent with area '" + area + "'");
        assertTrue(
            phoneMatchesAreaOrNeutralFallback(area, resultByField[2]),
            "phone '" + resultByField[2] + "' inconsistent with area '" + area + "'");
      }
    }
  }

  private static boolean phoneMatchesAreaOrNeutralFallback(String area, String phoneResult) {
    return switch (area) {
      case "LS" -> phoneResult.startsWith("0113 496 0");
      case "S" -> phoneResult.startsWith("0114 496 0");
      case "NG" -> phoneResult.startsWith("0115 496 0");
      case "LE" -> phoneResult.startsWith("0116 496 0");
      case "BS" -> phoneResult.startsWith("0117 496 0");
      case "B" -> phoneResult.startsWith("0121 496 0");
      case "EH" -> phoneResult.startsWith("0131 496 0");
      case "G" -> phoneResult.startsWith("0141 496 0");
      case "L" -> phoneResult.startsWith("0151 496 0");
      case "M" -> phoneResult.startsWith("0161 496 0");
      case "NE" -> phoneResult.startsWith("0191 498 0");
      case "BT" -> phoneResult.startsWith("028 9649 6");
      case "CF" -> phoneResult.startsWith("029 2018 0");
      case "E", "EC", "N", "NW", "SE", "SW", "W", "WC" -> phoneResult.startsWith("020 7946 0");
      default -> phoneResult.startsWith("01632 960"); // no range for this area: neutral fallback
    };
  }
}
