package org.identigon.alterego.strategy;

import org.identigon.alterego.Randomness;
import org.identigon.alterego.Strategy;
import org.identigon.alterego.TransformationContext;

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
