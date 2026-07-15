package io.github.dconneely.alterego.strategy;

import java.util.List;
import java.util.Map;

/**
 * Recognised legal-form suffixes {@code organisationName()} preserves when present in the input
 * (SPECIFICATION.md section 4.2). A fixed per-country list, not a downloaded dataset: UK's four
 * forms are the exact abbreviations Companies Act 2006 ss.58(2) (public limited company) and
 * 59(2) (private limited company) permit, including the Welsh-language alternatives a Welsh
 * company may use in place of the English forms.
 */
final class LegalSuffixes {

  private static final Map<String, List<String>> BY_COUNTRY = Map.of("GB", List.of("Ltd", "plc", "Cyf.", "c.c.c."));

  private LegalSuffixes() {}

  static List<String> forCountry(String country) {
    return BY_COUNTRY.getOrDefault(country, List.of());
  }
}
