package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.AlterEgoAttributes;
import io.github.dconneely.alterego.TransformationContext;
import io.github.dconneely.alterego.UkNation;

/**
 * Shared "establish the record's place" mechanism (SPECIFICATION.md section 6.2, section 6.3):
 * when {@code postcode()}/{@code phoneNumber()} is the first field to touch a record's place
 * (nothing fixed yet, but a real scope is active — {@link
 * io.github.dconneely.alterego.RecordAttributes#isActive()}), it fixes {@code
 * UK_POSTCODE_AREA}/{@code UK_NATION} together from a real town in the country's own dictionary
 * via {@code computeIfAbsent} — guaranteeing a later {@code city()} call in the same record
 * always finds a matching town, not just an arbitrary or fabricated area. Outside any scope,
 * callers must not invoke this at all (it costs randomness) — {@code isActive()} lets them check
 * that for free first.
 */
final class PlaceCoherence {

  private PlaceCoherence() {}

  /** Establishes (or returns the already-fixed) {@code UK_POSTCODE_AREA}, only call when {@code isActive()}. */
  static String establish(TransformationContext context, String country) {
    return context
        .record()
        .computeIfAbsent(
            AlterEgoAttributes.UK_POSTCODE_AREA,
            randomness -> {
              DictionaryEntry chosen = randomness.pick(DictionaryLoader.load(country, "towns").entries());
              context.record().set(AlterEgoAttributes.UK_NATION, UkNation.valueOf(chosen.tags().get(1)));
              return chosen.tags().get(0);
            });
  }
}
