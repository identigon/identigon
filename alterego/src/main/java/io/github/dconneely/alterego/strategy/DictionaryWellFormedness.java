package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.AlterEgoConfigException;
import io.github.dconneely.alterego.UkNation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structural checks beyond what {@link DictionaryParser} already enforces during parsing
 * (a valid provenance header with every required field): non-empty, no duplicate entries,
 * sorted, and the cited licence has committed text under {@code dictionaries/LICENCES/}.
 *
 * <p>"No duplicate entries" means no duplicate (value, tags) pair, not no duplicate value: a
 * tagged dictionary may legitimately repeat a value under different tags (e.g. London spans
 * several postcode areas, so it appears once per area with the area as part of the tag) —
 * only an exact repeated row is rejected.
 */
final class DictionaryWellFormedness {

  private static final Pattern POSTCODE_AREA = Pattern.compile("[A-Z]{1,2}");

  private DictionaryWellFormedness() {}

  static void validate(Dictionary dictionary, String resourceName, boolean licenceTextExists) {
    if (dictionary.entries().isEmpty()) {
      throw new AlterEgoConfigException(resourceName + ": dictionary is empty");
    }
    requireNoDuplicateEntries(dictionary, resourceName);
    requireSorted(dictionary, resourceName);
    if (!licenceTextExists) {
      throw new AlterEgoConfigException(
          resourceName
              + ": cites licence '"
              + dictionary.header().licence()
              + "' with no matching file under dictionaries/LICENCES/");
    }
  }

  /**
   * Validates the one-tag ({@code MODIFIER}/{@code NOUN}) convention for the organisation-name
   * components dictionary: every entry is tagged with the position(s) it may compose in, so
   * generation can avoid nonsensical pairings (e.g. two place-like modifiers together).
   */
  static void validateOrgComponentTags(Dictionary dictionary, String resourceName) {
    for (DictionaryEntry entry : dictionary.entries()) {
      if (entry.tags().size() != 1) {
        throw new AlterEgoConfigException(
            resourceName
                + ": organisation-component entry '"
                + entry.value()
                + "' must have exactly 1 tag (MODIFIER or NOUN), got "
                + entry.tags().size());
      }
      String tag = entry.tags().get(0);
      if (!tag.equals("MODIFIER") && !tag.equals("NOUN")) {
        throw new AlterEgoConfigException(
            resourceName
                + ": organisation-component entry '"
                + entry.value()
                + "' has invalid tag (must be MODIFIER or NOUN): "
                + tag);
      }
    }
  }

  /**
   * Validates the two-tag (display template, place) convention for the phone-ranges resource
   * (SPECIFICATION.md section 4.1, section 4.4, section 6.3; {@code docs/phone-ranges.md}): each
   * entry's value is an 8-digit fixed prefix, its first tag is that same prefix in Ofcom's own
   * digit-grouping with the 3 freely-varying trailing digits marked {@code XXX} (stripping the
   * template's spaces and {@code XXX} must reconstruct the value exactly), and its second tag is
   * either a 1–2 uppercase-letter postcode area (for record-coherence matching against {@code
   * UK_POSTCODE_AREA}), {@code NONE} (the designated geography-neutral fallback range), or
   * {@code MOBILE} (never a coherence match target or the neutral fallback).
   */
  static void validatePhoneRangeTags(Dictionary dictionary, String resourceName) {
    Pattern valuePattern = Pattern.compile("[0-9]{8}");
    for (DictionaryEntry entry : dictionary.entries()) {
      String value = entry.value();
      if (entry.tags().size() != 2) {
        throw new AlterEgoConfigException(
            resourceName
                + ": phone-range entry '"
                + value
                + "' must have exactly 2 tags (display template, place), got "
                + entry.tags().size());
      }
      if (!valuePattern.matcher(value).matches()) {
        throw new AlterEgoConfigException(
            resourceName + ": phone-range value must be exactly 8 digits, got: " + value);
      }
      String template = entry.tags().get(0);
      if (!template.endsWith("XXX") || template.indexOf("XXX") != template.length() - 3) {
        throw new AlterEgoConfigException(
            resourceName + ": phone-range template must end in exactly one 'XXX', got: " + template);
      }
      String reconstructed = template.replace(" ", "").replace("XXX", "");
      if (!reconstructed.equals(value)) {
        throw new AlterEgoConfigException(
            resourceName
                + ": phone-range template '"
                + template
                + "' does not reconstruct value '"
                + value
                + "' (got '"
                + reconstructed
                + "')");
      }
      String place = entry.tags().get(1);
      if (!place.equals("NONE") && !place.equals("MOBILE") && !POSTCODE_AREA.matcher(place).matches()) {
        throw new AlterEgoConfigException(
            resourceName
                + ": phone-range entry '"
                + value
                + "' has invalid place tag (must be NONE, MOBILE, or 1-2 uppercase letters): "
                + place);
      }
    }
  }

  /** Validates the two-tag (postcode area, {@link UkNation}) convention for town dictionaries. */
  static void validateTownTags(Dictionary dictionary, String resourceName) {
    for (DictionaryEntry entry : dictionary.entries()) {
      if (entry.tags().size() != 2) {
        throw new AlterEgoConfigException(
            resourceName
                + ": town entry '"
                + entry.value()
                + "' must have exactly 2 tags (postcode area, nation), got "
                + entry.tags().size());
      }
      String area = entry.tags().get(0);
      if (!POSTCODE_AREA.matcher(area).matches()) {
        throw new AlterEgoConfigException(
            resourceName + ": town entry '" + entry.value() + "' has invalid postcode area tag: " + area);
      }
      String nation = entry.tags().get(1);
      try {
        UkNation.valueOf(nation);
      } catch (IllegalArgumentException e) {
        throw new AlterEgoConfigException(
            resourceName + ": town entry '" + entry.value() + "' has invalid nation tag: " + nation);
      }
    }
  }

  private static void requireNoDuplicateEntries(Dictionary dictionary, String resourceName) {
    Set<String> seen = new HashSet<>();
    for (DictionaryEntry entry : dictionary.entries()) {
      String key = entry.value() + "\t" + String.join("\t", entry.tags());
      if (!seen.add(key)) {
        throw new AlterEgoConfigException(
            resourceName + ": duplicate entry (same value and tags): " + entry.value());
      }
    }
  }

  private static void requireSorted(Dictionary dictionary, String resourceName) {
    List<String> values = dictionary.values();
    for (int i = 1; i < values.size(); i++) {
      if (values.get(i - 1).compareTo(values.get(i)) > 0) {
        throw new AlterEgoConfigException(
            resourceName + ": entries not sorted: '" + values.get(i - 1) + "' before '" + values.get(i) + "'");
      }
    }
  }
}
