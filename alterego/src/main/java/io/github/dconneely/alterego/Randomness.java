package io.github.dconneely.alterego;

import java.util.List;

/**
 * Deterministic randomness for one strategy invocation. A stateful, single-threaded consumer of
 * an HMAC-SHA256 counter-mode byte stream derived from the input value: the sequence of calls a
 * strategy makes is part of its deterministic behaviour. Never backed by
 * {@link java.util.random.RandomGenerator} or any other JDK or third-party PRNG.
 */
public interface Randomness {

  /**
   * Returns a value drawn uniformly from {@code [0, bound)}. Requires {@code bound > 0}.
   *
   * @param bound the exclusive upper bound; must be {@code > 0}
   * @return a value in {@code [0, bound)}
   */
  int nextInt(int bound);

  /**
   * Returns a value drawn uniformly from {@code [0, bound)}. Requires {@code bound > 0}.
   *
   * @param bound the exclusive upper bound; must be {@code > 0}
   * @return a value in {@code [0, bound)}
   */
  long nextLong(long bound);

  /**
   * Returns a fair coin flip.
   *
   * @return a fair coin flip
   */
  boolean nextBoolean();

  /**
   * Returns a uniformly chosen element of {@code choices}. Requires a non-empty list.
   *
   * @param <T> the element type
   * @param choices the candidates to choose from; must be non-empty
   * @return a uniformly chosen element of {@code choices}
   */
  <T> T pick(List<T> choices);

  /**
   * Returns a digit.
   *
   * @return a digit in {@code '0'..'9'}
   */
  char digit();

  /**
   * Returns an upper-case letter.
   *
   * @return an upper-case letter in {@code 'A'..'Z'}
   */
  char letterUpper();

  /**
   * Returns a lower-case letter.
   *
   * @return a lower-case letter in {@code 'a'..'z'}
   */
  char letterLower();
}
