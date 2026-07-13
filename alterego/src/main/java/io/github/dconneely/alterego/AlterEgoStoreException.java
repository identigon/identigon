package io.github.dconneely.alterego;

/**
 * A {@code MappingStore} problem: required but not configured, a store operation failed, or a
 * stored value failed to decode into its canonical type.
 */
public class AlterEgoStoreException extends AlterEgoException {

  public AlterEgoStoreException(String message) {
    super(message);
  }

  public AlterEgoStoreException(String message, Throwable cause) {
    super(message, cause);
  }
}
