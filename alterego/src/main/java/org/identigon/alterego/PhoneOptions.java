package org.identigon.alterego;

/** Options for {@code phoneNumber()} (SPECIFICATION.md section 4.1, section 4.4). */
public final class PhoneOptions {

  private static final PhoneOptions DEFAULTS = new PhoneOptions(false, false);

  private final boolean realistic;
  private final boolean includeNonGeographic;

  private PhoneOptions(boolean realistic, boolean includeNonGeographic) {
    this.realistic = realistic;
    this.includeNonGeographic = includeNonGeographic;
  }

  /**
   * The fictionality guarantee applies where the country defines a reserved range.
   *
   * @return the default options
   */
  public static PhoneOptions defaults() {
    return DEFAULTS;
  }

  /**
   * Opts out of the fictionality guarantee (ADR 0005): every digit is replaced independently,
   * so the output may coincide with a real, connectable phone number.
   *
   * @return options that opt out of the fictionality guarantee
   */
  public static PhoneOptions realistic() {
    return new PhoneOptions(true, false);
  }

  /**
   * Includes the non-geographic (freephone, premium rate, UK-wide) drama ranges in the generation pool.
   *
   * @return options that include non-geographic ranges
   */
  public PhoneOptions includeNonGeographic() {
    return new PhoneOptions(realistic, true);
  }

  boolean isRealistic() {
    return realistic;
  }

  boolean isIncludeNonGeographic() {
    return includeNonGeographic;
  }
}
