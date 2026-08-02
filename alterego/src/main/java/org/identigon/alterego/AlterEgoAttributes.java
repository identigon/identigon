package org.identigon.alterego;

/**
 * Published {@link AttributeKey} constants the built-ins use to cohere within a record scope
 * (section 6.3). The underlying key strings (not these Java constant names) feed keyed-scope
 * derivation and, once shipped, are frozen — do not change either string after release, since
 * that would silently change every keyed-scope output.
 */
public final class AlterEgoAttributes {

  private AlterEgoAttributes() {}

  /** The postcode area (e.g. {@code "M"} for Manchester) fixed for the current record. */
  public static final AttributeKey<String> UK_POSTCODE_AREA =
      AttributeKey.of("alterego:gb-postcode-area", String.class);

  /** The nation implied by {@link #UK_POSTCODE_AREA} for the current record. */
  public static final AttributeKey<UkNation> UK_NATION =
      AttributeKey.of("alterego:gb-nation", UkNation.class);
}
