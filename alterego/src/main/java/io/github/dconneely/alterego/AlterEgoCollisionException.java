package io.github.dconneely.alterego;

/** A {@code unique()} transformation exhausted its retry budget without finding a free output. */
public class AlterEgoCollisionException extends AlterEgoException {

  /**
   * Creates the exception with a message.
   *
   * @param message a message describing the failure
   */
  public AlterEgoCollisionException(String message) {
    super(message);
  }
}
