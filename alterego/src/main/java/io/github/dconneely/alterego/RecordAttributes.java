package io.github.dconneely.alterego;

import java.util.Optional;
import java.util.function.Function;

/**
 * Typed, shared state for the fields of one record scope, reached from a strategy via
 * {@link TransformationContext#record()}. First touch wins: whichever field is transformed
 * first fixes an attribute, and later fields see that value. Outside any {@code RecordScope},
 * every method behaves as a no-op: {@link #get} returns empty, {@link #set} is discarded, and
 * {@link #computeIfAbsent} resolves without retaining, so strategy code is identical in and out
 * of a scope.
 */
public interface RecordAttributes {

  /** Returns the record's value for {@code key}, if one has been set or resolved. */
  <A> Optional<A> get(AttributeKey<A> key);

  /**
   * Resolves {@code key} if absent. The first caller's {@code resolver} fixes the value for the
   * whole record; later callers see that value without re-invoking {@code resolver}. In a keyed
   * record scope, {@code resolver} receives randomness derived from the record key and the
   * attribute name, independent of which field asks first; in an anonymous scope, it receives
   * the asking strategy's own randomness.
   */
  <A> A computeIfAbsent(AttributeKey<A> key, Function<Randomness, A> resolver);

  /**
   * Sets {@code key} to {@code value} if absent. Setting an already-fixed key to an equal value
   * is a no-op; setting it to a different value throws {@link AlterEgoCoherenceException}.
   */
  <A> void set(AttributeKey<A> key, A value);
}
