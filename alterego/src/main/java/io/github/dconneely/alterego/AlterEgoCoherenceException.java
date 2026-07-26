package io.github.dconneely.alterego;

/**
 * A record attribute was set to a value that conflicts with the one already fixed for the
 * current record scope.
 */
public class AlterEgoCoherenceException extends AlterEgoException {

  /**
   * Creates the exception with a message.
   *
   * @param message a message describing the conflict
   */
  public AlterEgoCoherenceException(String message) {
    super(message);
  }
}
