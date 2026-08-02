package org.identigon.alterego;

import java.util.Optional;
import java.util.function.Function;

/**
 * The {@link RecordAttributes} seen outside any {@code RecordScope} (section 6.2): nothing is
 * retained. {@code computeIfAbsent} still runs its resolver, against this context's own
 * {@link Randomness}, so strategy code behaves identically whether or not a scope is active.
 */
final class NoOpRecordAttributes implements RecordAttributes {

  private final Randomness randomness;

  NoOpRecordAttributes(Randomness randomness) {
    this.randomness = randomness;
  }

  @Override
  public <A> Optional<A> get(AttributeKey<A> key) {
    return Optional.empty();
  }

  @Override
  public <A> A computeIfAbsent(AttributeKey<A> key, Function<Randomness, A> resolver) {
    return resolver.apply(randomness);
  }

  @Override
  public <A> void set(AttributeKey<A> key, A value) {
    // Discarded: outside any scope, attributes are not retained (section 6.2).
  }

  @Override
  public boolean isActive() {
    return false;
  }
}
