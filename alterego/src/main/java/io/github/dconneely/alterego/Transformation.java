package io.github.dconneely.alterego;

import java.util.function.Function;

/**
 * A {@link Strategy} bound to an {@code AlterEgo} instance, ready to use in {@code .map(...)}.
 * Immutable and thread-safe; one instance may be shared across threads and reused across
 * streams. Honours the owning instance's {@link NullPolicy}.
 *
 * @param <T> the value type transformed
 */
public interface Transformation<T> extends Function<T, T> {

  /**
   * Returns a transformation guaranteeing distinct inputs map to distinct outputs. Subsumes
   * {@link #stored()}; idempotent; composes with {@code stored()} to the same effect either way
   * round. Requires a configured {@code MappingStore}, checked immediately, not per element.
   *
   * @return a uniqueness-guaranteeing transformation
   */
  Transformation<T> unique();

  /**
   * Returns a transformation that persists input-to-output mappings and reuses them on later
   * calls. Idempotent. Requires a configured {@code MappingStore}, checked immediately, not per
   * element.
   *
   * @return a mapping-persisting transformation
   */
  Transformation<T> stored();
}
