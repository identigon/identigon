package org.identigon.alterego;

/**
 * A {@code MappingStore} problem: required but not configured, a store operation failed, or a
 * stored value failed to decode into its canonical type.
 */
public class AlterEgoStoreException extends AlterEgoException {

  /**
   * Creates the exception with a message.
   *
   * @param message a message describing the store problem
   */
  public AlterEgoStoreException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a message and cause.
   *
   * @param message a message describing the store problem
   * @param cause the underlying cause
   */
  public AlterEgoStoreException(String message, Throwable cause) {
    super(message, cause);
  }
}
