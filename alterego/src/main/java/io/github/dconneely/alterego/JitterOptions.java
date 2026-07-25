package io.github.dconneely.alterego;

import java.util.Objects;

/**
 * Inclusive clamp bounds for a {@code shiftDate}/{@code shiftDateTime} jitter strategy
 * (SPECIFICATION.md section 4.5), applied last, after the strategy has run. {@code T} is
 * {@link java.time.LocalDate} or {@link java.time.LocalDateTime}, matching the method it is
 * passed to. Values that would fall outside a bound are clamped to it, not rejected — values
 * near a bound pile up on it, documented rather than hidden. There is no "unbounded" instance:
 * an unclamped call simply omits the trailing {@code JitterOptions} argument.
 *
 * @param <T> {@link java.time.LocalDate} or {@link java.time.LocalDateTime}
 */
public final class JitterOptions<T extends Comparable<? super T>> {

  private final T min;
  private final T max;

  private JitterOptions(T min, T max) {
    this.min = min;
    this.max = max;
  }

  /** Clamps the result to no earlier than {@code min} (inclusive). */
  public static <T extends Comparable<? super T>> JitterOptions<T> min(T min) {
    return new JitterOptions<>(Objects.requireNonNull(min, "min"), null);
  }

  /** Clamps the result to no later than {@code max} (inclusive). */
  public static <T extends Comparable<? super T>> JitterOptions<T> max(T max) {
    return new JitterOptions<>(null, Objects.requireNonNull(max, "max"));
  }

  /** Clamps the result to the inclusive range {@code [min, max]}. */
  public static <T extends Comparable<? super T>> JitterOptions<T> minmax(T min, T max) {
    Objects.requireNonNull(min, "min");
    Objects.requireNonNull(max, "max");
    if (min.compareTo(max) > 0) {
      throw new AlterEgoConfigException("min must not be after max, got min=" + min + ", max=" + max);
    }
    return new JitterOptions<>(min, max);
  }

  /** Applies the clamp, last, to a strategy's raw result. */
  T clamp(T value) {
    T result = value;
    if (min != null && result.compareTo(min) < 0) {
      result = min;
    }
    if (max != null && result.compareTo(max) > 0) {
      result = max;
    }
    return result;
  }
}
