package io.github.dconneely.alterego;

import io.github.dconneely.alterego.store.MappingStore;
import java.util.Locale;

/** The concrete {@link TransformationContext} the binding machinery creates per input value. */
final class DefaultTransformationContext implements TransformationContext {

  private final byte[] salt;
  private final Locale locale;
  private final String domain;
  private final MappingStore mappingStore;
  private final boolean rawMappingKeys;
  private final Randomness random;
  private final Mappings mappings;
  private RecordAttributes recordAttributes;

  private DefaultTransformationContext(
      byte[] salt,
      Locale locale,
      String domain,
      String canonical,
      int counter,
      MappingStore mappingStore,
      boolean rawMappingKeys) {
    this.salt = salt;
    this.locale = locale;
    this.domain = domain;
    this.mappingStore = mappingStore;
    this.rawMappingKeys = rawMappingKeys;
    this.random = Derivation.randomness(salt, domain, canonical, counter);
    this.mappings = new DefaultMappings(salt, domain, mappingStore, rawMappingKeys);
  }

  /** Creates the top-level context for a fresh input (Appendix A.1, counter 0). */
  static TransformationContext topLevel(
      byte[] salt,
      Locale locale,
      String domain,
      String canonical,
      MappingStore mappingStore,
      boolean rawMappingKeys) {
    return new DefaultTransformationContext(salt, locale, domain, canonical, 0, mappingStore, rawMappingKeys);
  }

  /** Creates a retry context for {@code unique()} collision escape (Appendix A.1). */
  static TransformationContext retry(
      byte[] salt,
      Locale locale,
      String domain,
      String canonical,
      int counter,
      MappingStore mappingStore,
      boolean rawMappingKeys) {
    return new DefaultTransformationContext(salt, locale, domain, canonical, counter, mappingStore, rawMappingKeys);
  }

  @Override
  public Randomness random() {
    return random;
  }

  @Override
  public Locale locale() {
    return locale;
  }

  @Override
  public String domain() {
    return domain;
  }

  @Override
  public Mappings mappings() {
    return mappings;
  }

  @Override
  public RecordAttributes record() {
    if (recordAttributes == null) {
      DefaultRecordScope active = DefaultRecordScope.current();
      recordAttributes = active != null ? active.viewFor(random) : new NoOpRecordAttributes(random);
    }
    return recordAttributes;
  }

  @Override
  public TransformationContext derived(String subDomain, String subInput) {
    DomainNames.requireValid(subDomain, "domain");
    return new DefaultTransformationContext(salt, locale, subDomain, subInput, 0, mappingStore, rawMappingKeys);
  }
}
