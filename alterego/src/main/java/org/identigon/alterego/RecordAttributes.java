package org.identigon.alterego;

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

  /**
   * Returns the record's value for {@code key}, if one has been set or resolved.
   *
   * @param <A> the attribute's value type
   * @param key the attribute key
   * @return the record's value for {@code key}, if any
   */
  <A> Optional<A> get(AttributeKey<A> key);

  /**
   * Resolves {@code key} if absent. The first caller's {@code resolver} fixes the value for the
   * whole record; later callers see that value without re-invoking {@code resolver}. In a keyed
   * record scope, {@code resolver} receives randomness derived from the record key and the
   * attribute name, independent of which field asks first; in an anonymous scope, it receives
   * the asking strategy's own randomness.
   *
   * @param <A> the attribute's value type
   * @param key the attribute key
   * @param resolver resolves the value if not already fixed
   * @return the now-fixed value
   */
  <A> A computeIfAbsent(AttributeKey<A> key, Function<Randomness, A> resolver);

  /**
   * Sets {@code key} to {@code value} if absent. Setting an already-fixed key to an equal value
   * is a no-op; setting it to a different value throws {@link AlterEgoCoherenceException}.
   *
   * @param <A> the attribute's value type
   * @param key the attribute key
   * @param value the value to fix {@code key} to
   */
  <A> void set(AttributeKey<A> key, A value);

  /**
   * Whether this view is backed by a real, active {@code RecordScope} ({@code true}) or the
   * outside-any-scope no-op view ({@code false}). Costs no randomness and touches no attribute,
   * unlike {@code get}/{@code computeIfAbsent}/{@code set} — for a strategy that needs to know
   * whether attempting to *establish* a shared attribute (not just read one) is worthwhile at
   * all, since outside a scope nothing set or resolved is ever retained.
   *
   * @return whether a real record scope is active
   */
  boolean isActive();
}
