package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Exercises the eight jitter strategies of SPECIFICATION.md section 4.5: uniform-in-range,
 * determinism, leap-year coverage, nanosecond-zeroing, the {@code start > end} rejection, and
 * {@link JitterOptions} clamp piling at both boundaries, for both {@code LocalDate} and
 * {@code LocalDateTime}.
 */
class TemporalJitterTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final LocalDate DAY = LocalDate.of(2026, 7, 25);
  private static final LocalDateTime MOMENT = LocalDateTime.of(2026, 7, 25, 14, 30, 15, 123_456_789);
  private static final Instant INSTANT_MOMENT = Instant.parse("2026-07-25T14:30:15.123456789Z");

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).build();
  }

  // --- shiftDate(int days) ----------------------------------------------------------------------

  @Test
  void shiftDateByDaysIsUniformInRange() {
    Transformation<LocalDate> t = alterego().shiftDate(30);
    for (int i = 0; i < 500; i++) {
      LocalDate result = t.apply(DAY.plusDays(i));
      long diff = ChronoUnit.DAYS.between(DAY.plusDays(i), result);
      assertTrue(diff >= -30 && diff <= 30, "diff out of range: " + diff);
    }
  }

  @Test
  void shiftDateByDaysIsDeterministic() {
    assertEquals(alterego().shiftDate(30).apply(DAY), alterego().shiftDate(30).apply(DAY));
  }

  @Test
  void shiftDateRejectsNegativeDays() {
    assertThrows(AlterEgoConfigException.class, () -> alterego().shiftDate(-1));
  }

  // --- shiftDate(DateField) ---------------------------------------------------------------------

  @Test
  void shiftDateByMonthStaysWithinInputsMonth() {
    Transformation<LocalDate> t = alterego().shiftDate(AlterEgo.DateField.MONTH);
    for (int i = 0; i < 200; i++) {
      LocalDate input = DAY.plusDays(i);
      LocalDate result = t.apply(input);
      assertEquals(input.getYear(), result.getYear());
      assertEquals(input.getMonth(), result.getMonth());
    }
  }

  @Test
  void shiftDateByYearStaysWithinInputsYearIncludingLeapDay() {
    Transformation<LocalDate> t = alterego().shiftDate(AlterEgo.DateField.YEAR);
    boolean sawLeapDay = false;
    for (int i = 0; i < 366; i++) { // 2024 is a leap year: valid day-of-year offsets are 0..365
      LocalDate input = LocalDate.of(2024, 1, 1).plusDays(i);
      LocalDate result = t.apply(input);
      assertEquals(2024, result.getYear());
      if (result.getMonthValue() == 2 && result.getDayOfMonth() == 29) {
        sawLeapDay = true;
      }
    }
    assertTrue(sawLeapDay, "never landed on the leap day across a leap year's inputs");
  }

  // --- shiftDateTime(int, int) -------------------------------------------------------------------

  @Test
  void shiftDateTimeByDaysAndSecondsIsUniformInRangeAndZerosNanos() {
    Transformation<LocalDateTime> t = alterego().shiftDateTime(30, 3600);
    for (int i = 0; i < 300; i++) {
      LocalDateTime input = MOMENT.plusHours(i);
      LocalDateTime result = t.apply(input);
      assertEquals(0, result.getNano());
      long dayDiff = ChronoUnit.DAYS.between(input.toLocalDate(), result.toLocalDate());
      assertTrue(dayDiff >= -30 && dayDiff <= 30, "date diff out of range: " + dayDiff);
    }
  }

  @Test
  void shiftDateTimeByDaysAndSecondsIsDeterministic() {
    assertEquals(
        alterego().shiftDateTime(30, 3600).apply(MOMENT), alterego().shiftDateTime(30, 3600).apply(MOMENT));
  }

  // --- shiftDateTime(int, LocalTime, LocalTime) ---------------------------------------------------

  @Test
  void shiftDateTimeByRangeStaysWithinStartAndEndAndZerosNanos() {
    LocalTime start = LocalTime.of(9, 0);
    LocalTime end = LocalTime.of(17, 0);
    Transformation<LocalDateTime> t = alterego().shiftDateTime(30, start, end);
    for (int i = 0; i < 300; i++) {
      LocalDateTime result = t.apply(MOMENT.plusHours(i));
      assertEquals(0, result.getNano());
      LocalTime resultTime = result.toLocalTime();
      assertFalse(resultTime.isBefore(start), "before start: " + resultTime);
      assertFalse(resultTime.isAfter(end), "after end: " + resultTime);
    }
  }

  @Test
  void shiftDateTimeRejectsStartAfterEnd() {
    assertThrows(
        AlterEgoConfigException.class,
        () -> alterego().shiftDateTime(30, LocalTime.of(17, 0), LocalTime.of(9, 0)));
  }

  // --- shiftDateTime(int, TimeField.HOUR) ---------------------------------------------------------

  @Test
  void shiftDateTimeByHourFieldKeepsInputsHourAndZerosNanos() {
    Transformation<LocalDateTime> t = alterego().shiftDateTime(30, AlterEgo.TimeField.HOUR);
    for (int i = 0; i < 200; i++) {
      LocalDateTime input = MOMENT.plusDays(i);
      LocalDateTime result = t.apply(input);
      assertEquals(0, result.getNano());
      assertEquals(input.getHour(), result.getHour());
    }
  }

  // --- shiftDateTime(DateField, ...) --------------------------------------------------------------

  @Test
  void shiftDateTimeByDateFieldAndSecondsZerosNanosAndStaysWithinMonth() {
    Transformation<LocalDateTime> t = alterego().shiftDateTime(AlterEgo.DateField.MONTH, 3600);
    for (int i = 0; i < 200; i++) {
      LocalDateTime input = MOMENT.plusDays(i);
      LocalDateTime result = t.apply(input);
      assertEquals(0, result.getNano());
      assertEquals(input.getYear(), result.getYear());
      assertEquals(input.getMonth(), result.getMonth());
    }
  }

  @Test
  void shiftDateTimeByDateFieldAndRangeStaysWithinStartAndEnd() {
    LocalTime start = LocalTime.of(9, 0);
    LocalTime end = LocalTime.of(17, 0);
    Transformation<LocalDateTime> t = alterego().shiftDateTime(AlterEgo.DateField.YEAR, start, end);
    for (int i = 0; i < 200; i++) {
      LocalDateTime result = t.apply(MOMENT.plusDays(i));
      LocalTime resultTime = result.toLocalTime();
      assertFalse(resultTime.isBefore(start));
      assertFalse(resultTime.isAfter(end));
    }
  }

  @Test
  void shiftDateTimeByDateFieldAndHourFieldKeepsInputsHour() {
    Transformation<LocalDateTime> t = alterego().shiftDateTime(AlterEgo.DateField.MONTH, AlterEgo.TimeField.HOUR);
    LocalDateTime result = t.apply(MOMENT);
    assertEquals(MOMENT.getHour(), result.getHour());
    assertEquals(0, result.getNano());
  }

  // --- JitterOptions clamping ----------------------------------------------------------------------

  @Test
  void clampPilesResultsAtTheMinBoundaryForLocalDate() {
    JitterOptions<LocalDate> options = JitterOptions.min(DAY);
    Transformation<LocalDate> t = alterego().shiftDate(365, options);
    for (int i = 0; i < 100; i++) {
      LocalDate result = t.apply(DAY.plusDays(i));
      assertFalse(result.isBefore(DAY), "result before the min bound: " + result);
    }
  }

  @Test
  void clampPilesResultsAtTheMaxBoundaryForLocalDate() {
    JitterOptions<LocalDate> options = JitterOptions.max(DAY);
    Transformation<LocalDate> t = alterego().shiftDate(365, options);
    for (int i = 0; i < 100; i++) {
      LocalDate result = t.apply(DAY.plusDays(i));
      assertFalse(result.isAfter(DAY), "result after the max bound: " + result);
    }
  }

  @Test
  void clampAppliesToShiftDateTimeToo() {
    JitterOptions<LocalDateTime> options = JitterOptions.minmax(MOMENT.minusDays(1), MOMENT.plusDays(1));
    Transformation<LocalDateTime> t = alterego().shiftDateTime(365, 3600, options);
    for (int i = 0; i < 100; i++) {
      LocalDateTime result = t.apply(MOMENT.plusHours(i));
      assertFalse(result.isBefore(MOMENT.minusDays(1)));
      assertFalse(result.isAfter(MOMENT.plusDays(1)));
    }
  }

  // --- shiftInstant(int, int) --------------------------------------------------------------------

  @Test
  void shiftInstantByDaysAndSecondsIsUniformInRangeAndPreservesNanos() {
    Transformation<Instant> t = alterego().shiftInstant(30, 3600);
    for (int i = 0; i < 300; i++) {
      Instant input = INSTANT_MOMENT.plusSeconds(i * 3600L);
      Instant result = t.apply(input);
      assertEquals(123_456_789, result.getNano());
      long secDiff = result.getEpochSecond() - input.getEpochSecond();
      long maxDiff = 30L * 86400L + 3600L;
      assertTrue(secDiff >= -maxDiff && secDiff <= maxDiff, "sec diff out of range: " + secDiff);
    }
  }

  @Test
  void shiftInstantByDaysAndSecondsIsDeterministic() {
    assertEquals(
        alterego().shiftInstant(30, 3600).apply(INSTANT_MOMENT), alterego().shiftInstant(30, 3600).apply(INSTANT_MOMENT));
  }

  @Test
  void clampAppliesToShiftInstantToo() {
    JitterOptions<Instant> options = JitterOptions.minmax(INSTANT_MOMENT.minus(1, ChronoUnit.DAYS), INSTANT_MOMENT.plus(1, ChronoUnit.DAYS));
    Transformation<Instant> t = alterego().shiftInstant(365, 3600, options);
    for (int i = 0; i < 100; i++) {
      Instant result = t.apply(INSTANT_MOMENT.plusSeconds(i * 3600L));
      assertFalse(result.isBefore(INSTANT_MOMENT.minus(1, ChronoUnit.DAYS)));
      assertFalse(result.isAfter(INSTANT_MOMENT.plus(1, ChronoUnit.DAYS)));
    }
  }
}
