package io.github.dconneely.alterego.pattern;

import io.github.dconneely.alterego.Mappings;
import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.RecordAttributes;
import io.github.dconneely.alterego.TransformationContext;
import java.util.Locale;

/** A minimal {@link TransformationContext} test double exposing only a chosen {@link Randomness}. */
final class FakeContext implements TransformationContext {

  private final Randomness random;

  FakeContext(Randomness random) {
    this.random = random;
  }

  @Override
  public Randomness random() {
    return random;
  }

  @Override
  public Locale locale() {
    return Locale.UK;
  }

  @Override
  public String domain() {
    return "test:fake";
  }

  @Override
  public Mappings mappings() {
    throw new UnsupportedOperationException("not needed by PatternStrategyTest");
  }

  @Override
  public RecordAttributes record() {
    throw new UnsupportedOperationException("not needed by PatternStrategyTest");
  }

  @Override
  public TransformationContext derived(String subDomain, String subInput) {
    throw new UnsupportedOperationException("not needed by PatternStrategyTest");
  }
}
