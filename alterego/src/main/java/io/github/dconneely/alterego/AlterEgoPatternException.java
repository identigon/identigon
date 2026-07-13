package io.github.dconneely.alterego;

/** A malformed pattern passed to {@code AlterEgo.pattern(String)}, reported with its position. */
public class AlterEgoPatternException extends AlterEgoConfigException {

  private final int position;

  public AlterEgoPatternException(String message, int position) {
    super(message);
    this.position = position;
  }

  /** The zero-based index into the pattern string where the error was found. */
  public int position() {
    return position;
  }
}
