package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;

/** Implements the normative GB driving licence number generation algorithm (SPECIFICATION.md A.7). */
public final class DrivingLicenceNumberStrategy implements Strategy<String> {
  /** Singleton instance. */
  public static final DrivingLicenceNumberStrategy INSTANCE = new DrivingLicenceNumberStrategy();

  private DrivingLicenceNumberStrategy() {}

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    char decade = random.digit();
    boolean female = random.nextBoolean();
    int month = random.nextInt(12) + 1;
    int day = random.nextInt(28) + 1;
    char yearUnit = random.digit();
    char i1 = random.letterUpper();
    char i2 = random.letterUpper();
    char t1 = random.letterUpper();
    char t2 = random.letterUpper();
    
    int mm = month + (female ? 50 : 0);
    
    return String.format("99999%c%02d%02d%c%c%c9%c%c", 
        decade, mm, day, yearUnit, i1, i2, t1, t2);
  }
}
