package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class JitterOptionsTest {

  private static final LocalDate DAY = LocalDate.of(2026, 7, 25);

  @Test
  void minClampsValuesBelowIt() {
    JitterOptions<LocalDate> options = JitterOptions.min(DAY);
    assertEquals(DAY, options.clamp(DAY.minusDays(1)));
    assertEquals(DAY.plusDays(1), options.clamp(DAY.plusDays(1)));
  }

  @Test
  void maxClampsValuesAboveIt() {
    JitterOptions<LocalDate> options = JitterOptions.max(DAY);
    assertEquals(DAY, options.clamp(DAY.plusDays(1)));
    assertEquals(DAY.minusDays(1), options.clamp(DAY.minusDays(1)));
  }

  @Test
  void minmaxClampsBothDirections() {
    JitterOptions<LocalDate> options = JitterOptions.minmax(DAY.minusDays(5), DAY.plusDays(5));
    assertEquals(DAY.minusDays(5), options.clamp(DAY.minusDays(100)));
    assertEquals(DAY.plusDays(5), options.clamp(DAY.plusDays(100)));
    assertEquals(DAY, options.clamp(DAY));
  }

  @Test
  void minmaxRejectsMinAfterMax() {
    assertThrows(
        AlterEgoConfigException.class, () -> JitterOptions.minmax(DAY.plusDays(1), DAY.minusDays(1)));
  }

  @Test
  void minRejectsNull() {
    assertThrows(NullPointerException.class, () -> JitterOptions.min(null));
  }
}
