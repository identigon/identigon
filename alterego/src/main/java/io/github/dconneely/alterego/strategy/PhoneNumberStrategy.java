package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.util.List;

/**
 * Generates a fictional phone number (SPECIFICATION.md section 4.1, section 4.4): digits
 * replaced in place, punctuation and grouping preserved. By default, if the country has a
 * published fictional-range table ({@code dictionaries/<country>/phone-ranges.txt} —
 * {@code docs/phone-ranges.md} for GB's sourcing), the output is built from one of those ranges'
 * display templates (its fixed digits copied verbatim, its trailing {@code XXX} replaced with 3
 * random digits), guaranteeing an unconnectable number. A country with no range table falls back
 * to plain in-place digit replacement, silently and without a fictionality guarantee — a
 * documented lesser category (section 4.1), not a failure. {@code realistic} always uses that
 * plain fallback, even for a country with a range table.
 */
public final class PhoneNumberStrategy implements Strategy<String> {

  /** Non-null only when generating from a country's reserved-range templates. */
  private final List<String> templates;

  private PhoneNumberStrategy(List<String> templates) {
    this.templates = templates;
  }

  public static PhoneNumberStrategy forCountry(String country, boolean realistic) {
    if (realistic || !DictionaryLoader.exists(country, "phone-ranges")) {
      return new PhoneNumberStrategy(null);
    }
    Dictionary dictionary = DictionaryLoader.load(country, "phone-ranges");
    List<String> templates = dictionary.entries().stream().map(entry -> entry.tags().get(0)).toList();
    return new PhoneNumberStrategy(templates);
  }

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    if (templates == null) {
      return replaceDigitsInPlace(input, random);
    }
    return fillTemplate(random.pick(templates), random);
  }

  private static String replaceDigitsInPlace(String input, Randomness random) {
    StringBuilder sb = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      sb.append(c >= '0' && c <= '9' ? random.digit() : c);
    }
    return sb.toString();
  }

  /** Copies the template's fixed prefix verbatim and replaces its trailing {@code XXX} with 3 random digits. */
  private static String fillTemplate(String template, Randomness random) {
    int xxxIndex = template.length() - 3;
    return template.substring(0, xxxIndex) + random.digit() + random.digit() + random.digit();
  }
}
