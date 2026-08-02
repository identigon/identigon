package org.identigon.alterego.strategy;

import java.util.List;
import java.util.Optional;
import org.identigon.alterego.AlterEgoAttributes;
import org.identigon.alterego.Randomness;
import org.identigon.alterego.Strategy;
import org.identigon.alterego.TransformationContext;
import org.identigon.alterego.UkNation;

/**
 * Replacement drawn from the country's town/city dictionary (SPECIFICATION.md section 4.3,
 * section 6.3): inside a record scope with an already-fixed {@code UK_POSTCODE_AREA}, restricts
 * the pick to towns tagged with that exact area; otherwise picks freely and fixes both {@code
 * UK_POSTCODE_AREA} and {@code UK_NATION} from the chosen town's tags, so a later {@code
 * postcode()}/{@code phoneNumber()} in the same scope can follow. Outside any scope (or with
 * nothing yet fixed), this is exactly the unconstrained pick over the same underlying rows in the
 * same order as before record coherence existed — byte-identical output.
 *
 * <p>If an area is fixed but matches no town in the dictionary (e.g. a caller pre-seeded {@code
 * UK_POSTCODE_AREA} to a value with no town entry), this falls back to an unconstrained pick —
 * still deterministic, since the fallback consumes the same context randomness either way, the
 * same family of fallback as {@code NameOptions.preserveInitial()}'s no-matching-initial case.
 * Documented v1 limitation, not silently dropped.
 *
 * <p>Each attribute is set from the chosen town's tags whenever it is not already present (not
 * only when *neither* was fixed yet): if the area was already fixed (by a pre-seed, or
 * established by {@code postcode()}/{@code phoneNumber()} running first) but {@code UK_NATION}
 * was not — since neither of those builtins knows a nation, only an area — this is what fixes it
 * too, consistently with the chosen town. Checking presence first, rather than calling {@code
 * set} unconditionally and relying on its no-op-if-equal behaviour, matters specifically for the
 * unmatched-fixed-area fallback above: the chosen (unconstrained) town's own area will generally
 * *disagree* with an already-fixed-but-unmatched area, which would otherwise throw
 * {@link org.identigon.alterego.AlterEgoCoherenceException} instead of silently degrading.
 */
public final class CityStrategy implements Strategy<String> {

  private final List<DictionaryEntry> entries;

  private CityStrategy(List<DictionaryEntry> entries) {
    this.entries = entries;
  }

  /**
   * Creates a strategy for {@code country}.
   *
   * @param country the ISO 3166-1 alpha-2 country to load the town dictionary for
   * @return a strategy for that country
   */
  public static CityStrategy forCountry(String country) {
    return new CityStrategy(DictionaryLoader.load(country, "towns").entries());
  }

  @Override
  public String transform(String input, TransformationContext context) {
    Optional<String> fixedArea = context.record().get(AlterEgoAttributes.UK_POSTCODE_AREA);
    List<DictionaryEntry> candidates = entries;
    if (fixedArea.isPresent()) {
      List<DictionaryEntry> matching = entries.stream().filter(e -> e.tags().get(0).equals(fixedArea.get())).toList();
      if (!matching.isEmpty()) {
        candidates = matching;
      }
    }
    Randomness random = context.random();
    DictionaryEntry chosen = random.pick(candidates);
    if (fixedArea.isEmpty()) {
      context.record().set(AlterEgoAttributes.UK_POSTCODE_AREA, chosen.tags().get(0));
    }
    if (context.record().get(AlterEgoAttributes.UK_NATION).isEmpty()) {
      context.record().set(AlterEgoAttributes.UK_NATION, UkNation.valueOf(chosen.tags().get(1)));
    }
    return chosen.value();
  }
}
