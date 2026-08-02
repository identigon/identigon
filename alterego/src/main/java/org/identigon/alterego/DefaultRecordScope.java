package org.identigon.alterego;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The concrete {@link RecordScope}: a plain (single-thread use is already a documented
 * precondition — section 6.1), non-concurrent map of fixed attributes, plus an optional record
 * key ({@code null} for an anonymous scope). Made visible to whatever {@link
 * TransformationContext} gets created while {@link #apply} is running via a thread-local — the
 * only way to reach it, since {@code Transformation<T>} is a plain {@code Function<T, T>} with no
 * scope parameter of its own.
 */
final class DefaultRecordScope implements RecordScope {

  private static final ThreadLocal<DefaultRecordScope> ACTIVE = new ThreadLocal<>();

  private final byte[] salt;
  private final String recordKey;
  private final Map<AttributeKey<?>, Object> attributes = new HashMap<>();

  DefaultRecordScope(byte[] salt, String recordKey) {
    this.salt = salt;
    this.recordKey = recordKey;
  }

  /** The scope currently installed on this thread, if any. */
  static DefaultRecordScope current() {
    return ACTIVE.get();
  }

  @Override
  public <T> T apply(Transformation<T> transformation, T value) {
    DefaultRecordScope previous = ACTIVE.get();
    ACTIVE.set(this);
    try {
      return transformation.apply(value);
    } finally {
      ACTIVE.set(previous);
    }
  }

  @Override
  public <A> RecordScope with(AttributeKey<A> key, A value) {
    set(key, value);
    return this;
  }

  @Override
  public void close() {
    attributes.clear();
  }

  /** A {@link RecordAttributes} view of this scope, for a context whose own randomness is {@code contextRandomness}. */
  RecordAttributes viewFor(Randomness contextRandomness) {
    return new ScopedRecordAttributes(this, contextRandomness);
  }

  @SuppressWarnings("unchecked")
  <A> Optional<A> get(AttributeKey<A> key) {
    return Optional.ofNullable((A) attributes.get(key));
  }

  @SuppressWarnings("unchecked")
  <A> A computeIfAbsent(AttributeKey<A> key, Function<Randomness, A> resolver, Randomness anonymousFallback) {
    if (attributes.containsKey(key)) {
      return (A) attributes.get(key);
    }
    Randomness randomness =
        recordKey != null ? Derivation.recordRandomness(salt, key.name(), recordKey) : anonymousFallback;
    A resolved = resolver.apply(randomness);
    attributes.put(key, resolved);
    return resolved;
  }

  <A> void set(AttributeKey<A> key, A value) {
    Objects.requireNonNull(value, "value");
    Object existing = attributes.get(key);
    if (existing == null) {
      attributes.put(key, value);
    } else if (!existing.equals(value)) {
      throw new AlterEgoCoherenceException(
          "Conflicting value for record attribute '"
              + key.name()
              + "': already fixed to "
              + existing
              + ", got "
              + value);
    }
  }
}
