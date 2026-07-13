package io.github.dconneely.alterego;

/** Root of every unchecked exception this library throws. */
public class AlterEgoException extends RuntimeException {

  public AlterEgoException(String message) {
    super(message);
  }

  public AlterEgoException(String message, Throwable cause) {
    super(message, cause);
  }
}
