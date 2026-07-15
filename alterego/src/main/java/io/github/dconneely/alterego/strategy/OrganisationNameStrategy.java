package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.util.List;

/**
 * Composes an organisation name from the country's tagged component dictionary
 * (SPECIFICATION.md section 4.2): three distinct words, {@code [MODIFIER-or-NOUN] + NOUN + NOUN}
 * — position 1 may be either category, positions 2 and 3 must be {@code NOUN} and distinct from
 * every word already chosen, so the same word can never repeat and two place-like
 * {@code MODIFIER} words can never land next to each other (docs/dictionaries.md,
 * "Organisation-name components", has the full reasoning). A recognised legal suffix detected at
 * the end of the input (case-insensitive) is preserved on the output in its canonical form;
 * {@link LegalSuffixes} holds the fixed per-country list.
 */
public final class OrganisationNameStrategy implements Strategy<String> {

  private final List<String> allWords;
  private final List<String> nouns;
  private final List<String> suffixes;

  private OrganisationNameStrategy(List<String> allWords, List<String> nouns, List<String> suffixes) {
    this.allWords = allWords;
    this.nouns = nouns;
    this.suffixes = suffixes;
  }

  public static OrganisationNameStrategy forCountry(String country) {
    Dictionary dictionary = DictionaryLoader.load(country, "organisation-components");
    List<String> nouns =
        dictionary.entries().stream()
            .filter(entry -> entry.tags().get(0).equals("NOUN"))
            .map(DictionaryEntry::value)
            .toList();
    return new OrganisationNameStrategy(dictionary.values(), nouns, LegalSuffixes.forCountry(country));
  }

  @Override
  public String transform(String input, TransformationContext context) {
    String detectedSuffix = detectSuffix(input);

    String word1 = context.random().pick(allWords);
    List<String> remainingAfterWord1 = nouns.stream().filter(w -> !w.equals(word1)).toList();
    String word2 = context.random().pick(remainingAfterWord1);
    List<String> remainingAfterWord2 = remainingAfterWord1.stream().filter(w -> !w.equals(word2)).toList();
    String word3 = context.random().pick(remainingAfterWord2);

    String body = word1 + " " + word2 + " " + word3;
    return detectedSuffix == null ? body : body + " " + detectedSuffix;
  }

  private String detectSuffix(String input) {
    if (input == null) {
      return null;
    }
    String trimmed = input.strip();
    for (String suffix : suffixes) {
      if (trimmed.equalsIgnoreCase(suffix)) {
        return suffix;
      }
      String withSeparator = " " + suffix;
      int start = trimmed.length() - withSeparator.length();
      if (start > 0 && trimmed.regionMatches(true, start, withSeparator, 0, withSeparator.length())) {
        return suffix;
      }
    }
    return null;
  }
}
