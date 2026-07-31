package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a fictional phone number (SPECIFICATION.md section 4.1, section 4.4, section 6.3):
 * digits replaced in place, punctuation and grouping preserved. By default, if the country has a
 * published fictional-range table ({@code dictionaries/<country>/phone-ranges.txt} —
 * {@code docs/phone-ranges.md} for GB's sourcing), the output is built from one of those ranges'
 * display templates (its fixed digits copied verbatim, its trailing {@code XXX} replaced with 3
 * random digits), guaranteeing an unconnectable number.
 *
 * <p>Inside an active record scope, prefers the range tagged with the record's place — already
 * fixed, or, if this is the first field to touch the record's place, freshly established from a
 * real town ({@link PlaceCoherence}, so a later {@code city()} call always agrees); if no range
 * matches that place, uses the {@code NONE}-tagged (geography-neutral, {@code 01632 960xxx})
 * range specifically — never an arbitrary other geographic range, and never the {@code
 * MOBILE}-tagged range. Outside any scope, this is the unchanged, unconstrained pick over every
 * shipped range.
 *
 * <p>A country with no range table falls back to plain in-place digit replacement, silently and
 * without a fictionality guarantee — a documented lesser category (section 4.1), not a failure.
 * {@code realistic} always uses that plain fallback, even for a country with a range table.
 */
public final class PhoneNumberStrategy implements Strategy<String> {

  /** Non-null only when generating from a country's reserved-range templates. */
  private final List<String> allTemplates;

  private final Map<String, String> templateByArea;
  private final String neutralFallbackTemplate;
  private final String country;

  private PhoneNumberStrategy(
      List<String> allTemplates, Map<String, String> templateByArea, String neutralFallbackTemplate, String country) {
    this.allTemplates = allTemplates;
    this.templateByArea = templateByArea;
    this.neutralFallbackTemplate = neutralFallbackTemplate;
    this.country = country;
  }

  /**
   * Creates a strategy for {@code country}.
   *
   * @param country the ISO 3166-1 alpha-2 country to load phone ranges for
   * @param realistic whether to opt out of the fictionality guarantee
   * @param includeNonGeographic whether to include non-geographic (freephone, premium, UK-wide) ranges
   * @return a strategy for that country
   */
  public static PhoneNumberStrategy forCountry(String country, boolean realistic, boolean includeNonGeographic) {
    if (realistic || !DictionaryLoader.exists(country, "phone-ranges")) {
      return new PhoneNumberStrategy(null, Map.of(), null, country);
    }
    Dictionary dictionary = DictionaryLoader.load(country, "phone-ranges");
    // distinct(): a range's template may appear on more than one row (e.g. London's 8 postcode
    // areas all sharing one range) purely for area-matching purposes; the unconstrained pool
    // must still treat it as a single choice, or the pick bound (and so the golden outputs)
    // would shift purely from that duplication.
    List<String> allTemplates = dictionary.entries().stream()
        .filter(entry -> {
          String place = entry.tags().get(1);
          if (place.equals("FREEPHONE") || place.equals("PREMIUM") || place.equals("UK_WIDE")) {
            return includeNonGeographic;
          }
          return true;
        })
        .map(entry -> entry.tags().get(0))
        .distinct()
        .toList();
    Map<String, String> templateByArea = new HashMap<>();
    String neutralFallbackTemplate = null;
    for (DictionaryEntry entry : dictionary.entries()) {
      String template = entry.tags().get(0);
      String place = entry.tags().get(1);
      if (place.equals("NONE")) {
        neutralFallbackTemplate = template;
      } else if (!place.equals("MOBILE") && !place.equals("FREEPHONE") && !place.equals("PREMIUM") && !place.equals("UK_WIDE")) {
        templateByArea.put(place, template);
      }
    }
    return new PhoneNumberStrategy(allTemplates, templateByArea, neutralFallbackTemplate, country);
  }

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    if (allTemplates == null) {
      return replaceDigitsInPlace(input, random);
    }
    String template =
        context.record().isActive()
            ? templateForFixedArea(PlaceCoherence.establish(context, country), random)
            : random.pick(allTemplates);
    return fillTemplate(template, random);
  }

  private String templateForFixedArea(String area, Randomness random) {
    String matching = templateByArea.get(area);
    if (matching != null) {
      return matching;
    }
    return neutralFallbackTemplate != null ? neutralFallbackTemplate : random.pick(allTemplates);
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
