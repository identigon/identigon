package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;

/** Implements the normative NHS number generation algorithm (SPECIFICATION.md A.5). */
public final class NhsNumberStrategy implements Strategy<String> {
  /** Singleton instance. */
  public static final NhsNumberStrategy INSTANCE = new NhsNumberStrategy();

  private NhsNumberStrategy() {}

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    int[] w = { 10, 9, 8, 7, 6, 5, 4, 3, 2 };
    int[] digits = new int[9];
    digits[0] = 9; digits[1] = 9; digits[2] = 9;
    int c = -1;
    
    while (true) {
      for (int i = 3; i < 9; i++) {
        digits[i] = random.digit() - '0';
      }
      int sum = 0;
      for (int i = 0; i < 9; i++) {
        sum += digits[i] * w[i];
      }
      c = 11 - (sum % 11);
      if (c == 11) c = 0;
      if (c != 10) break;
    }
    
    StringBuilder sb = new StringBuilder(14);
    sb.append("999 ");
    for (int i = 3; i < 6; i++) sb.append(digits[i]);
    sb.append(' ');
    for (int i = 6; i < 9; i++) sb.append(digits[i]);
    sb.append(c);
    
    return sb.toString();
  }
}
