package io.github.dconneely.alterego;

/** Options for {@code firstName()} and {@code lastName()} (SPECIFICATION.md section 4.2). */
public final class NameOptions {

  private static final NameOptions DEFAULTS = new NameOptions(false);

  private final boolean preserveInitial;

  private NameOptions(boolean preserveInitial) {
    this.preserveInitial = preserveInitial;
  }

  /**
   * No options set.
   *
   * @return the default options
   */
  public static NameOptions defaults() {
    return DEFAULTS;
  }

  /**
   * The output starts with the same letter as the input. If the dictionary has no entry with
   * that initial, this is ignored for that input (unconstrained pick, still deterministic).
   *
   * @return options that preserve the input's initial letter
   */
  public static NameOptions preserveInitial() {
    return new NameOptions(true);
  }

  boolean isPreserveInitial() {
    return preserveInitial;
  }
}
