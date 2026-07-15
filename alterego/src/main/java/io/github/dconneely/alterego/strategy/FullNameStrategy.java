package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Tokenises and delegates to the first-name/surname strategies (SPECIFICATION.md section 4.2,
 * the pinned rules): trim and split on whitespace; a single token is transformed as a surname;
 * with two or more tokens, the first and any middle tokens use the first-name domain and the
 * last token uses the surname domain; hyphenated tokens are split and rejoined; blank input is
 * returned unchanged. Each part goes through {@code context.derived(...)} with the built-in
 * domain, so results agree with standalone {@code firstName()}/{@code lastName()} by
 * construction.
 */
public final class FullNameStrategy implements Strategy<String> {

  public static final String FIRST_NAME_DOMAIN = "alterego:first-name";
  public static final String LAST_NAME_DOMAIN = "alterego:last-name";

  private final NameDictionaryStrategy firstNameStrategy;
  private final NameDictionaryStrategy lastNameStrategy;

  private FullNameStrategy(NameDictionaryStrategy firstNameStrategy, NameDictionaryStrategy lastNameStrategy) {
    this.firstNameStrategy = firstNameStrategy;
    this.lastNameStrategy = lastNameStrategy;
  }

  public static FullNameStrategy forCountry(String country) {
    NameDictionaryStrategy firstNames = NameDictionaryStrategy.forDictionary(country, "first-names", false);
    NameDictionaryStrategy lastNames = NameDictionaryStrategy.forDictionary(country, "surnames", false);
    return new FullNameStrategy(firstNames, lastNames);
  }

  @Override
  public String transform(String input, TransformationContext context) {
    if (input.isBlank()) {
      return input;
    }
    String[] rawTokens = input.strip().split("\\s+");
    List<String> transformed = new ArrayList<>(rawTokens.length);

    if (rawTokens.length == 1) {
      transformed.add(transformToken(rawTokens[0], LAST_NAME_DOMAIN, lastNameStrategy, context));
    } else {
      for (int i = 0; i < rawTokens.length; i++) {
        boolean isLast = i == rawTokens.length - 1;
        String domain = isLast ? LAST_NAME_DOMAIN : FIRST_NAME_DOMAIN;
        NameDictionaryStrategy strategy = isLast ? lastNameStrategy : firstNameStrategy;
        transformed.add(transformToken(rawTokens[i], domain, strategy, context));
      }
    }
    return String.join(" ", transformed);
  }

  private static String transformToken(
      String token, String domain, NameDictionaryStrategy strategy, TransformationContext context) {
    if (token.contains("-")) {
      String[] segments = token.split("-", -1);
      List<String> transformedSegments = new ArrayList<>(segments.length);
      for (String segment : segments) {
        transformedSegments.add(transformSegment(segment, domain, strategy, context));
      }
      return String.join("-", transformedSegments);
    }
    return transformSegment(token, domain, strategy, context);
  }

  private static String transformSegment(
      String segment, String domain, NameDictionaryStrategy strategy, TransformationContext context) {
    TransformationContext derived = context.derived(domain, segment);
    return strategy.transform(segment, derived);
  }
}
