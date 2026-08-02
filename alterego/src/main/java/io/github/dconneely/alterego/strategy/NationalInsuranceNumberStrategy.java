package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.util.List;

/** Implements the normative National Insurance number generation algorithm (SPECIFICATION.md A.6). */
public final class NationalInsuranceNumberStrategy implements Strategy<String> {
  /** Singleton instance. */
  public static final NationalInsuranceNumberStrategy INSTANCE = new NationalInsuranceNumberStrategy();

  private static final List<String> SUFFIXES = List.of("A", "B", "C", "D");

  private NationalInsuranceNumberStrategy() {}

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    char[] d = new char[6];
    for (int i = 0; i < 6; i++) d[i] = random.digit();
    String s = random.pick(SUFFIXES);

    return "QQ " + d[0] + d[1] + " " + d[2] + d[3] + " " + d[4] + d[5] + " " + s;
  }
}
