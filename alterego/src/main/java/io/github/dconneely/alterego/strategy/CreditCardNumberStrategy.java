package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;

/** Implements the normative credit card number generation algorithm (SPECIFICATION.md A.9). */
public final class CreditCardNumberStrategy implements Strategy<String> {
  /** Singleton instance. */
  public static final CreditCardNumberStrategy INSTANCE = new CreditCardNumberStrategy();

  private CreditCardNumberStrategy() {}

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    int[] payload = new int[15];
    payload[0] = 0;
    for (int i = 1; i < 15; i++) {
      payload[i] = random.digit() - '0';
    }
    
    int sum = 0;
    for (int i = 0; i <= 14; i++) {
      int v = payload[14 - i];
      if (i % 2 == 0) {
        v = 2 * v;
        if (v > 9) v = v - 9;
      }
      sum += v;
    }
    int c = (10 - (sum % 10)) % 10;
    
    StringBuilder sb = new StringBuilder(19);
    for (int i = 0; i < 15; i++) {
      if (i > 0 && i % 4 == 0) sb.append(' ');
      sb.append(payload[i]);
    }
    sb.append(c);
    
    return sb.toString();
  }
}
