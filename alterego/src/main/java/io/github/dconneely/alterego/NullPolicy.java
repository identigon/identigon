package io.github.dconneely.alterego;

/** How a {@link Transformation} handles a {@code null} input value. */
public enum NullPolicy {

  /** {@code apply(null)} returns {@code null} without invoking the underlying strategy. */
  PASS_THROUGH,

  /** {@code apply(null)} throws {@link AlterEgoException}. */
  FAIL
}
