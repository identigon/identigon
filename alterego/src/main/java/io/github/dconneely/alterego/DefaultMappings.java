package io.github.dconneely.alterego;

import io.github.dconneely.alterego.store.MappingStore;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The {@link Mappings} view exposed to strategies: namespaced to one domain, hashing every key
 * exactly as Appendix A.4 hashes mapping-store keys, so raw input data never reaches the store.
 */
final class DefaultMappings implements Mappings {

  private static final HexFormat HEX = HexFormat.of();

  private final byte[] salt;
  private final String domain;
  private final MappingStore store;

  DefaultMappings(byte[] salt, String domain, MappingStore store) {
    this.salt = salt;
    this.domain = domain;
    this.store = store;
  }

  @Override
  public Optional<String> get(String canonicalKey) {
    return store().get(domain, hash(canonicalKey));
  }

  @Override
  public String putIfAbsent(String canonicalKey, String value) {
    return store().putIfAbsent(domain, hash(canonicalKey), value);
  }

  private MappingStore store() {
    if (store == null) {
      throw new AlterEgoStoreException("No MappingStore configured for domain: " + domain);
    }
    return store;
  }

  private String hash(String canonicalKey) {
    byte[] key = Derivation.deriveKey(salt, Derivation.PURPOSE_MAPKEY, domain, canonicalKey, 0);
    return HEX.formatHex(key);
  }
}
