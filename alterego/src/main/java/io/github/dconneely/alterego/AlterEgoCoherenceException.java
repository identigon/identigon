package io.github.dconneely.alterego;

/**
 * A record attribute was set to a value that conflicts with the one already fixed for the
 * current record scope.
 */
public class AlterEgoCoherenceException extends AlterEgoException {

  public AlterEgoCoherenceException(String message) {
    super(message);
  }
}
