package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** {@code city()}'s record-coherence behaviour (SPECIFICATION.md section 6.3). */
class CityCoherenceTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static AlterEgo gb() {
    return AlterEgo.builder().salt(SALT).build();
  }

  @Test
  void picksATownConsistentWithAnAlreadyFixedArea() {
    // "LS" has exactly one town (Leeds) in the shipped GB dictionary.
    AlterEgo eg = gb();
    Transformation<String> city = eg.city();
    try (RecordScope rec = eg.record().with(AlterEgoAttributes.UK_POSTCODE_AREA, "LS")) {
      assertEquals("Leeds", rec.apply(city, "original"));
    }
  }

  @Test
  void firstCityCallFixesBothAreaAndNation() {
    AlterEgo eg = gb();
    Transformation<String> city = eg.city();
    Strategy<String> readsNation =
        (in, ctx) -> ctx.record().get(AlterEgoAttributes.UK_NATION).map(Enum::name).orElse("none");
    Transformation<String> nationReader = eg.bind("test:reads-nation", readsNation);

    try (RecordScope rec = eg.record()) {
      rec.apply(city, "original");
      String nation = rec.apply(nationReader, "ignored");
      assertNotNull(nation);
      // Leeds -> ENGLAND, Belfast -> NORTHERN_IRELAND, Edinburgh/Glasgow -> SCOTLAND, Cardiff ->
      // WALES: whichever town was picked, some nation must have been fixed (not "none").
      assertNotEquals("none", nation);
    }
  }

  @Test
  void fixedAreaWithNoMatchingTownFallsBackToAnUnconstrainedPickWithoutThrowing() {
    AlterEgo eg = gb();
    Transformation<String> city = eg.city();
    try (RecordScope rec = eg.record().with(AlterEgoAttributes.UK_POSTCODE_AREA, "ZZ")) {
      String result = rec.apply(city, "original");
      assertNotNull(result);
    }
  }

  @Test
  void secondCityCallInTheSameScopeAgreesWithTheFirst() {
    AlterEgo eg = gb();
    Transformation<String> city = eg.city();
    try (RecordScope rec = eg.record()) {
      String first = rec.apply(city, "input-a");
      String second = rec.apply(city, "input-b");
      // Both must be consistent with the same fixed area, which for most GB areas means the
      // same single town (only London spans multiple areas but always resolves to "London").
      assertEquals(first, second);
    }
  }
}
