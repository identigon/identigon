package org.identigon.alterego.store;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A {@link MappingStore} backed by in-memory maps. Its memory use grows without bound with the
 * number of distinct inputs; large or long-lived datasets belong in an external store.
 */
public final class InMemoryMappingStore implements MappingStore {

  /** One namespace's forward (key to value) and inverse (value to key) maps. */
  private static final class Namespace {
    private final Map<String, String> forward = new ConcurrentHashMap<>();
    private final Map<String, String> inverse = new ConcurrentHashMap<>();
  }

  private final ConcurrentMap<String, Namespace> namespaces = new ConcurrentHashMap<>();

  /** Creates an empty store. */
  public InMemoryMappingStore() {}

  @Override
  public Optional<String> get(String namespace, String key) {
    return Optional.ofNullable(namespace(namespace).forward.get(key));
  }

  @Override
  public String putIfAbsent(String namespace, String key, String value) {
    Namespace ns = namespace(namespace);
    String existing = ns.forward.putIfAbsent(key, value);
    return existing != null ? existing : value;
  }

  @Override
  public PutUniqueResult putIfAbsentUnique(String namespace, String key, String value) {
    Namespace ns = namespace(namespace);
    // A per-namespace lock: the check (key unmapped AND value unused) and the write of both
    // maps must happen as one atomic unit, which plain ConcurrentHashMap operations on two
    // separate maps cannot guarantee alone.
    synchronized (ns) {
      String existingForKey = ns.forward.get(key);
      if (existingForKey != null) {
        return new PutUniqueResult.ExistingMapping(existingForKey);
      }
      if (ns.inverse.containsKey(value)) {
        return new PutUniqueResult.ValueTaken();
      }
      ns.forward.put(key, value);
      ns.inverse.put(value, key);
      return new PutUniqueResult.Stored();
    }
  }

  private Namespace namespace(String name) {
    return namespaces.computeIfAbsent(name, ignored -> new Namespace());
  }
}
