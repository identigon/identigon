package org.identigon.alterego.strategy;

import org.identigon.alterego.Strategy;
import org.identigon.alterego.TransformationContext;

/**
 * Composes a street address from the country's street dictionaries (SPECIFICATION.md section
 * 4.3): a house number drawn deterministically from 1-299, plus a complete street name (a theme
 * word plus a type word, e.g. "Victoria Road" — docs/dictionaries.md, "Street names").
 */
public final class StreetAddressStrategy implements Strategy<String> {

  private static final int MIN_HOUSE_NUMBER = 1;
  private static final int MAX_HOUSE_NUMBER = 299;

  private final Dictionary themes;
  private final Dictionary types;

  private StreetAddressStrategy(Dictionary themes, Dictionary types) {
    this.themes = themes;
    this.types = types;
  }

  /**
   * Creates a strategy for {@code country}.
   *
   * @param country the ISO 3166-1 alpha-2 country to load street dictionaries for
   * @return a strategy for that country
   */
  public static StreetAddressStrategy forCountry(String country) {
    Dictionary themes = DictionaryLoader.load(country, "street-themes");
    Dictionary types = DictionaryLoader.load(country, "street-types");
    return new StreetAddressStrategy(themes, types);
  }

  @Override
  public String transform(String input, TransformationContext context) {
    int houseNumber = MIN_HOUSE_NUMBER + context.random().nextInt(MAX_HOUSE_NUMBER - MIN_HOUSE_NUMBER + 1);
    String theme = context.random().pick(themes.values());
    String type = context.random().pick(types.values());
    return houseNumber + " " + theme + " " + type;
  }
}
