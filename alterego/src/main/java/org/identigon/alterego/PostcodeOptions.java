package org.identigon.alterego;

/** Options for {@code postcode()} (SPECIFICATION.md section 4.1, section 4.3). */
public final class PostcodeOptions {

  private static final PostcodeOptions DEFAULTS = new PostcodeOptions(false);

  private final boolean realistic;

  private PostcodeOptions(boolean realistic) {
    this.realistic = realistic;
  }

  /**
   * The fictionality guarantee applies: the inward code ends in a letter never used ({@code C I K M O V}).
   *
   * @return the default options
   */
  public static PostcodeOptions defaults() {
    return DEFAULTS;
  }

  /**
   * Opts out of the fictionality guarantee (ADR 0005): the inward code's last letter is drawn
   * from the full alphabet, so the output may coincide with a real, deliverable postcode.
   *
   * @return options that opt out of the fictionality guarantee
   */
  public static PostcodeOptions realistic() {
    return new PostcodeOptions(true);
  }

  boolean isRealistic() {
    return realistic;
  }
}
