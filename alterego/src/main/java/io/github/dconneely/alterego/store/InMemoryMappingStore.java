package io.github.dconneely.alterego.store;

import java.util.Optional;

/**
 * A {@link MappingStore} backed by in-memory maps. Its memory use grows without bound with the
 * number of distinct inputs; large or long-lived datasets belong in an external store.
 */
public final class InMemoryMappingStore implements MappingStore {

  public InMemoryMappingStore() {}

  @Override
  public Optional<String> get(String namespace, String key) {
    throw new UnsupportedOperationException("M4");
  }

  @Override
  public String putIfAbsent(String namespace, String key, String value) {
    throw new UnsupportedOperationException("M4");
  }

  @Override
  public PutUniqueResult putIfAbsentUnique(String namespace, String key, String value) {
    throw new UnsupportedOperationException("M4");
  }
}
