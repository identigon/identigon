package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Spec section 6.3's own example of a custom strategy joining built-in coherence: a Companies
 * House-style reference number reads (or, if it runs first, resolves) {@code UK_NATION} and picks
 * its prefix accordingly (SC for Scotland, NI for Northern Ireland, none for England/Wales).
 */
class CustomCoherenceTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static AlterEgo gb() {
    return AlterEgo.builder().salt(SALT).build();
  }

  private static final Strategy<String> COMPANIES_HOUSE_NUMBER =
      (in, ctx) -> {
        UkNation nation =
            ctx.record()
                .computeIfAbsent(AlterEgoAttributes.UK_NATION, random -> random.pick(java.util.List.of(UkNation.values())));
        String prefix =
            switch (nation) {
              case SCOTLAND -> "SC";
              case NORTHERN_IRELAND -> "NI";
              case ENGLAND, WALES -> "";
            };
        return prefix + String.format("%06d", Math.abs(in.hashCode() % 1_000_000));
      };

  @Test
  void followsCityChosenNationForItsPrefix() {
    AlterEgo eg = gb();
    Transformation<String> city = eg.city();
    Transformation<String> companiesHouse = eg.bind("test:companies-house", COMPANIES_HOUSE_NUMBER);

    try (RecordScope rec = eg.record().with(AlterEgoAttributes.UK_POSTCODE_AREA, "EH")) {
      // "EH" -> Edinburgh -> SCOTLAND, so the reference number must carry the "SC" prefix.
      rec.apply(city, "input");
      String reference = rec.apply(companiesHouse, "input");
      assertTrue(reference.startsWith("SC"), "expected an SC-prefixed reference: " + reference);
    }
  }

  @Test
  void noPrefixForEnglandOrWales() {
    AlterEgo eg = gb();
    Transformation<String> city = eg.city();
    Transformation<String> companiesHouse = eg.bind("test:companies-house", COMPANIES_HOUSE_NUMBER);

    try (RecordScope rec = eg.record().with(AlterEgoAttributes.UK_POSTCODE_AREA, "LS")) {
      // "LS" -> Leeds -> ENGLAND.
      rec.apply(city, "input");
      String reference = rec.apply(companiesHouse, "input");
      assertTrue(
          reference.length() == 6 && reference.chars().allMatch(Character::isDigit),
          "expected a plain 6-digit reference with no prefix: " + reference);
    }
  }

  @Test
  void canAlsoResolveTheNationItselfWhenRunFirst() {
    AlterEgo eg = gb();
    Transformation<String> companiesHouse = eg.bind("test:companies-house", COMPANIES_HOUSE_NUMBER);
    try (RecordScope rec = eg.record()) {
      String first = rec.apply(companiesHouse, "input");
      String second = rec.apply(companiesHouse, "input-2");
      // Both draws share the same resolved UK_NATION, so both must agree on prefix presence.
      assertTrue(first.startsWith("SC") == second.startsWith("SC"));
      assertTrue(first.startsWith("NI") == second.startsWith("NI"));
    }
  }
}
