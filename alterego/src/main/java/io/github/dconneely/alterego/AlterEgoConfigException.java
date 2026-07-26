package io.github.dconneely.alterego;

/**
 * A creation-time configuration error: no resources for the locale's country, an unsupported
 * value type, an invalid domain, invalid options, or an incompatible jitter unit. Always thrown
 * when a transformation is created, never per element.
 */
public class AlterEgoConfigException extends AlterEgoException {

  /**
   * Creates the exception with a message.
   *
   * @param message a message describing the configuration problem
   */
  public AlterEgoConfigException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a message and cause.
   *
   * @param message a message describing the configuration problem
   * @param cause the underlying cause
   */
  public AlterEgoConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
