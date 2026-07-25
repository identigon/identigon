package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.AlterEgo;
import io.github.dconneely.alterego.AlterEgoConfigException;
import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Date-part jitter (SPECIFICATION.md section 4.5, Appendix A.3): either a whole-day shift
 * uniform over {@code [-days, +days]}, or a uniform random day within the input's own month or
 * year (leap-aware for {@code YEAR}). Also used, via its package-private helpers, as the date
 * component of {@link DateTimeJitterStrategy}.
 */
public final class DateJitterStrategy implements Strategy<LocalDate> {

  private final Integer days;
  private final AlterEgo.DateField field;

  private DateJitterStrategy(Integer days, AlterEgo.DateField field) {
    this.days = days;
    this.field = field;
  }

  public static DateJitterStrategy byDays(int days) {
    requireNonNegative(days, "days");
    return new DateJitterStrategy(days, null);
  }

  public static DateJitterStrategy byField(AlterEgo.DateField field) {
    return new DateJitterStrategy(null, Objects.requireNonNull(field, "field"));
  }

  static void requireNonNegative(int value, String name) {
    if (value < 0) {
      throw new AlterEgoConfigException(name + " must be >= 0, got: " + value);
    }
  }

  @Override
  public LocalDate transform(LocalDate input, TransformationContext context) {
    Randomness random = context.random();
    return days != null ? shiftByDays(input, random, days) : shiftByField(input, random, field);
  }

  /** Appendix A.3 jitter shift over {@code [-n, +n]}: {@code nextLong(2n + 1) - n}. */
  static LocalDate shiftByDays(LocalDate input, Randomness random, int days) {
    return input.plusDays(random.nextLong(2L * days + 1) - days);
  }

  static LocalDate shiftByField(LocalDate input, Randomness random, AlterEgo.DateField field) {
    return switch (field) {
      case MONTH -> input.withDayOfMonth(1).plusDays(random.nextInt(input.lengthOfMonth()));
      case YEAR -> LocalDate.ofYearDay(input.getYear(), 1 + random.nextInt(input.lengthOfYear()));
    };
  }
}
