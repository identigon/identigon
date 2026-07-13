package io.github.dconneely.alterego;

import java.util.Optional;

/**
 * A domain-scoped view of the configured mapping store, for maintaining persistent, cross-record
 * relationships between values. Keys are hashed before reaching the store, so this view never
 * exposes or accepts raw store keys. For ephemeral, intra-record consistency, use
 * {@link TransformationContext#record()} instead.
 */
public interface Mappings {

  /** Looks up a previously stored value for {@code canonicalKey}, if any. */
  Optional<String> get(String canonicalKey);

  /**
   * Stores {@code value} under {@code canonicalKey} if absent, atomically. Returns the value
   * now associated with the key: {@code value} itself, or an existing value on a race.
   */
  String putIfAbsent(String canonicalKey, String value);
}
