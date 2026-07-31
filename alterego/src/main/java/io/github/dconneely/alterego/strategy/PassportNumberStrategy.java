package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;

/** Implements the normative UK passport number generation algorithm (SPECIFICATION.md A.8). */
public final class PassportNumberStrategy implements Strategy<String> {
  /** Singleton instance. */
  public static final PassportNumberStrategy INSTANCE = new PassportNumberStrategy();

  private PassportNumberStrategy() {}

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    char[] d = new char[7];
    for (int i = 0; i < 7; i++) d[i] = random.digit();
    return "ZZ" + new String(d);
  }
}
