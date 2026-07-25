package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.AlterEgo;
import io.github.dconneely.alterego.AlterEgoConfigException;
import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Date-and-time jitter (SPECIFICATION.md section 4.5): pairs one of the two date strategies
 * (shared with {@link DateJitterStrategy}) with one of three time strategies, the date part
 * always drawn before the time part. Nanoseconds are zeroed in every output, unconditionally,
 * regardless of which time strategy ran.
 *
 * <p>The {@code int seconds} time strategy shifts the time-of-day only (wrapping within the
 * same day via {@code Math.floorMod}), independently of the date part, matching the other two
 * time strategies, which are likewise bounded to a single day.
 */
public final class DateTimeJitterStrategy implements Strategy<LocalDateTime> {

  private static final int SECONDS_PER_DAY = 86_400;

  private final Integer days;
  private final AlterEgo.DateField dateField;
  private final Integer seconds;
  private final LocalTime start;
  private final LocalTime end;
  private final AlterEgo.TimeField timeField;

  private DateTimeJitterStrategy(
      Integer days,
      AlterEgo.DateField dateField,
      Integer seconds,
      LocalTime start,
      LocalTime end,
      AlterEgo.TimeField timeField) {
    this.days = days;
    this.dateField = dateField;
    this.seconds = seconds;
    this.start = start;
    this.end = end;
    this.timeField = timeField;
  }

  public static DateTimeJitterStrategy of(int days, int seconds) {
    DateJitterStrategy.requireNonNegative(days, "days");
    requireNonNegativeSeconds(seconds);
    return new DateTimeJitterStrategy(days, null, seconds, null, null, null);
  }

  public static DateTimeJitterStrategy of(int days, LocalTime start, LocalTime end) {
    DateJitterStrategy.requireNonNegative(days, "days");
    requireValidRange(start, end);
    return new DateTimeJitterStrategy(days, null, null, start, end, null);
  }

  public static DateTimeJitterStrategy of(int days, AlterEgo.TimeField timeField) {
    DateJitterStrategy.requireNonNegative(days, "days");
    return new DateTimeJitterStrategy(days, null, null, null, null, Objects.requireNonNull(timeField, "timeField"));
  }

  public static DateTimeJitterStrategy of(AlterEgo.DateField dateField, int seconds) {
    Objects.requireNonNull(dateField, "dateField");
    requireNonNegativeSeconds(seconds);
    return new DateTimeJitterStrategy(null, dateField, seconds, null, null, null);
  }

  public static DateTimeJitterStrategy of(AlterEgo.DateField dateField, LocalTime start, LocalTime end) {
    Objects.requireNonNull(dateField, "dateField");
    requireValidRange(start, end);
    return new DateTimeJitterStrategy(null, dateField, null, start, end, null);
  }

  public static DateTimeJitterStrategy of(AlterEgo.DateField dateField, AlterEgo.TimeField timeField) {
    return new DateTimeJitterStrategy(
        null,
        Objects.requireNonNull(dateField, "dateField"),
        null,
        null,
        null,
        Objects.requireNonNull(timeField, "timeField"));
  }

  private static void requireNonNegativeSeconds(int seconds) {
    DateJitterStrategy.requireNonNegative(seconds, "seconds");
  }

  private static void requireValidRange(LocalTime start, LocalTime end) {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (start.isAfter(end)) {
      throw new AlterEgoConfigException("start must not be after end, got start=" + start + ", end=" + end);
    }
  }

  @Override
  public LocalDateTime transform(LocalDateTime input, TransformationContext context) {
    Randomness random = context.random();
    LocalDate inputDate = input.toLocalDate();
    LocalDate newDate =
        days != null
            ? DateJitterStrategy.shiftByDays(inputDate, random, days)
            : DateJitterStrategy.shiftByField(inputDate, random, dateField);
    LocalTime newTime = shiftTime(input.toLocalTime(), random);
    return LocalDateTime.of(newDate, newTime);
  }

  private LocalTime shiftTime(LocalTime input, Randomness random) {
    if (seconds != null) {
      long shift = random.nextLong(2L * seconds + 1) - seconds;
      int newSecondOfDay = Math.floorMod(input.toSecondOfDay() + shift, SECONDS_PER_DAY);
      return LocalTime.ofSecondOfDay(newSecondOfDay);
    }
    if (start != null) {
      int startSecond = start.toSecondOfDay();
      int endSecond = end.toSecondOfDay();
      return LocalTime.ofSecondOfDay(startSecond + random.nextInt(endSecond - startSecond + 1));
    }
    int minute = random.nextInt(60);
    int second = random.nextInt(60);
    return LocalTime.of(input.getHour(), minute, second);
  }
}
