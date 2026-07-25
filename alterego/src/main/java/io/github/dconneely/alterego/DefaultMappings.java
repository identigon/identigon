package io.github.dconneely.alterego;

import io.github.dconneely.alterego.store.MappingStore;
import java.util.Optional;

/**
 * The {@link Mappings} view exposed to strategies: namespaced to one domain, hashing every key
 * exactly as Appendix A.4 hashes mapping-store keys, so raw input data never reaches the store.
 */
final class DefaultMappings implements Mappings {

  private final byte[] salt;
  private final String domain;
  private final MappingStore store;
  private final boolean rawMappingKeys;

  DefaultMappings(byte[] salt, String domain, MappingStore store, boolean rawMappingKeys) {
    this.salt = salt;
    this.domain = domain;
    this.store = store;
    this.rawMappingKeys = rawMappingKeys;
  }

  @Override
  public Optional<String> get(String canonicalKey) {
    return store().get(domain, key(canonicalKey));
  }

  @Override
  public String putIfAbsent(String canonicalKey, String value) {
    return store().putIfAbsent(domain, key(canonicalKey), value);
  }

  private MappingStore store() {
    if (store == null) {
      throw new AlterEgoStoreException("No MappingStore configured for domain: " + domain);
    }
    return store;
  }

  private String key(String canonicalKey) {
    return Derivation.mapKey(salt, domain, canonicalKey, rawMappingKeys);
  }
}
