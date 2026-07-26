package io.github.dconneely.alterego.store;

import java.util.Optional;

/**
 * The SPI a persistent input-to-output mapping store implements to back {@code stored()} and
 * {@code unique()}. Implementations must be thread-safe: transformations run under parallel
 * streams. Keys and values are opaque strings; by default the library writes hashed keys, never
 * raw input data.
 */
public interface MappingStore {

  /**
   * Looks up the value stored under {@code key} in {@code namespace}, if any.
   *
   * @param namespace the namespace (a transformation's domain)
   * @param key the store key
   * @return the stored value, if any
   */
  Optional<String> get(String namespace, String key);

  /**
   * Stores {@code value} under {@code key} in {@code namespace} if absent, atomically. Returns
   * the value now associated with the key: {@code value} itself, or an existing value on a race.
   *
   * @param namespace the namespace (a transformation's domain)
   * @param key the store key
   * @param value the value to store if the key is absent
   * @return the value now associated with the key
   */
  String putIfAbsent(String namespace, String key, String value);

  /**
   * Stores {@code key} to {@code value} in {@code namespace} only if {@code key} has no mapping
   * <em>and</em> {@code value} is not already in use as an output in {@code namespace}. The
   * whole check-and-store is atomic (one transaction for a store backed by a database).
   *
   * @param namespace the namespace (a transformation's domain)
   * @param key the store key
   * @param value the candidate value
   * @return the outcome of the attempt
   */
  PutUniqueResult putIfAbsentUnique(String namespace, String key, String value);

  /** The outcome of {@link #putIfAbsentUnique}. */
  sealed interface PutUniqueResult {

    /** The value was stored: it was a genuinely new, unused mapping. */
    record Stored() implements PutUniqueResult {}

    /**
     * {@code key} already had a mapping.
     *
     * @param value the value it already maps to
     */
    record ExistingMapping(String value) implements PutUniqueResult {}

    /** {@code key} had no mapping, but the requested value is already in use for another key. */
    record ValueTaken() implements PutUniqueResult {}
  }
}
