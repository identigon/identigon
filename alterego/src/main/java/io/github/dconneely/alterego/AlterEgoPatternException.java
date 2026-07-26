package io.github.dconneely.alterego;

/** A malformed pattern passed to {@code AlterEgo.pattern(String)}, reported with its position. */
public class AlterEgoPatternException extends AlterEgoConfigException {

  /** The zero-based index into the pattern string where the error was found. */
  private final int position;

  /**
   * Creates the exception with a message and offending position.
   *
   * @param message a message describing the malformed pattern
   * @param position the zero-based index into the pattern string where the error was found
   */
  public AlterEgoPatternException(String message, int position) {
    super(message);
    this.position = position;
  }

  /**
   * The zero-based index into the pattern string where the error was found.
   *
   * @return the offending position
   */
  public int position() {
    return position;
  }
}
