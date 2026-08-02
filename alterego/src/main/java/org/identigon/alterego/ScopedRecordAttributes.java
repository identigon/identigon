package org.identigon.alterego;

import java.util.Optional;
import java.util.function.Function;

/**
 * The {@link RecordAttributes} seen inside an active {@link RecordScope} (section 6.2): reads
 * and writes go to the scope's shared map, so every context created while the scope is active —
 * including {@code derived(...)} children — sees the same fixed attributes. Carries the *asking*
 * context's own {@link Randomness}, used only as the anonymous-scope {@code computeIfAbsent}
 * fallback (a keyed scope ignores it, resolving from the record key instead).
 */
final class ScopedRecordAttributes implements RecordAttributes {

  private final DefaultRecordScope scope;
  private final Randomness contextRandomness;

  ScopedRecordAttributes(DefaultRecordScope scope, Randomness contextRandomness) {
    this.scope = scope;
    this.contextRandomness = contextRandomness;
  }

  @Override
  public <A> Optional<A> get(AttributeKey<A> key) {
    return scope.get(key);
  }

  @Override
  public <A> A computeIfAbsent(AttributeKey<A> key, Function<Randomness, A> resolver) {
    return scope.computeIfAbsent(key, resolver, contextRandomness);
  }

  @Override
  public <A> void set(AttributeKey<A> key, A value) {
    scope.set(key, value);
  }

  @Override
  public boolean isActive() {
    return true;
  }
}
