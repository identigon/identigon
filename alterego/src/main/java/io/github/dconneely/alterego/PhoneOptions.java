package io.github.dconneely.alterego;

/** Options for {@code phoneNumber()} (SPECIFICATION.md section 4.1, section 4.4). */
public final class PhoneOptions {

  private static final PhoneOptions DEFAULTS = new PhoneOptions(false);

  private final boolean realistic;

  private PhoneOptions(boolean realistic) {
    this.realistic = realistic;
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
    return new PhoneOptions(true);
  }

  boolean isRealistic() {
    return realistic;
  }
}
