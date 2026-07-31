package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.JitterOptions;
import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.time.Instant;

/**
 * Instant jitter: shifts the instant by up to a given number of days and seconds.
 */
public final class InstantJitterStrategy implements Strategy<Instant> {

  private final int days;
  private final int seconds;

  private InstantJitterStrategy(int days, int seconds) {
    this.days = days;
    this.seconds = seconds;
  }

  /**
   * Creates a new instant jitter strategy.
   *
   * @param days the half-range of the date shift, in days
   * @param seconds the half-range of the time shift, in seconds
   * @return a new strategy
   */
  public static InstantJitterStrategy of(int days, int seconds) {
    return new InstantJitterStrategy(days, seconds);
  }

  @Override
  public Instant transform(Instant input, TransformationContext context) {
    Randomness random = context.random();
    long daysShift = days == 0 ? 0 : random.nextLong(2L * days + 1) - days;
    long secondsShift = seconds == 0 ? 0 : random.nextLong(2L * seconds + 1) - seconds;
    return input.plusSeconds(daysShift * 86400L + secondsShift);
  }
}
