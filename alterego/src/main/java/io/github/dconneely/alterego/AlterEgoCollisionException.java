package io.github.dconneely.alterego;

/** A {@code unique()} transformation exhausted its retry budget without finding a free output. */
public class AlterEgoCollisionException extends AlterEgoException {

  public AlterEgoCollisionException(String message) {
    super(message);
  }
}
