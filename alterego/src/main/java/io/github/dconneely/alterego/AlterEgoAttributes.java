package io.github.dconneely.alterego;

/**
 * Published {@link AttributeKey} constants the GB built-ins use to cohere within a record scope
 * (section 6.3). Their names feed keyed-scope derivation and are frozen (see {@code CLAUDE.md}).
 */
public final class AlterEgoAttributes {

  private AlterEgoAttributes() {}

  /** The GB postcode area (e.g. {@code "M"} for Manchester) fixed for the current record. */
  public static final AttributeKey<String> GB_POSTCODE_AREA =
      AttributeKey.of("alterego:gb-postcode-area", String.class);

  /** The UK country implied by {@link #GB_POSTCODE_AREA} for the current record. */
  public static final AttributeKey<GbCountry> GB_COUNTRY =
      AttributeKey.of("alterego:gb-country", GbCountry.class);
}
