package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.util.List;

/**
 * Picks an entry from a flat, country-scoped dictionary (SPECIFICATION.md section 4.2:
 * {@code firstName()}, {@code lastName()}; also reused for {@code city()}, which needs the same
 * unconstrained-pick behaviour and ignores the town dictionary's tag columns — those are read
 * directly by record-coherence code in M5, not through this strategy). {@code preserveInitial}
 * restricts the pick to entries sharing the input's first letter, falling back to an
 * unconstrained pick when no entry matches — still deterministic, since the fallback pick
 * consumes the same context randomness either way.
 */
public final class NameDictionaryStrategy implements Strategy<String> {

  private final Dictionary dictionary;
  private final boolean preserveInitial;

  private NameDictionaryStrategy(Dictionary dictionary, boolean preserveInitial) {
    this.dictionary = dictionary;
    this.preserveInitial = preserveInitial;
  }

  /**
   * Creates a strategy over {@code dictionaryName} for {@code country}.
   *
   * @param country the ISO 3166-1 alpha-2 country to load the dictionary for
   * @param dictionaryName the dictionary's file name (without the {@code .txt} extension)
   * @param preserveInitial whether to restrict picks to entries sharing the input's first letter
   * @return a strategy over that dictionary
   */
  public static NameDictionaryStrategy forDictionary(String country, String dictionaryName, boolean preserveInitial) {
    Dictionary dictionary = DictionaryLoader.load(country, dictionaryName);
    return new NameDictionaryStrategy(dictionary, preserveInitial);
  }

  @Override
  public String transform(String input, TransformationContext context) {
    List<String> candidates = dictionary.values();
    if (preserveInitial && input != null && !input.isEmpty()) {
      char initial = Character.toUpperCase(input.charAt(0));
      List<String> matchingInitial =
          candidates.stream()
              .filter(name -> !name.isEmpty() && Character.toUpperCase(name.charAt(0)) == initial)
              .toList();
      if (!matchingInitial.isEmpty()) {
        candidates = matchingInitial;
      }
    }
    return context.random().pick(candidates);
  }
}
