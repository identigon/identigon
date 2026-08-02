package org.identigon.alterego;

/** Root of every unchecked exception this library throws. */
public class AlterEgoException extends RuntimeException {

  /**
   * Creates the exception with a message.
   *
   * @param message a message describing the failure
   */
  public AlterEgoException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a message and cause.
   *
   * @param message a message describing the failure
   * @param cause the underlying cause
   */
  public AlterEgoException(String message, Throwable cause) {
    super(message, cause);
  }
}
