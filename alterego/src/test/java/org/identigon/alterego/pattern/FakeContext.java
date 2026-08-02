package org.identigon.alterego.pattern;

import java.util.Locale;
import org.identigon.alterego.Mappings;
import org.identigon.alterego.Randomness;
import org.identigon.alterego.RecordAttributes;
import org.identigon.alterego.TransformationContext;

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
