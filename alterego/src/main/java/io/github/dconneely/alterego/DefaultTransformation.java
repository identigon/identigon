package io.github.dconneely.alterego;

import io.github.dconneely.alterego.store.MappingStore;
import java.util.Locale;

/** The concrete {@link Transformation} returned by {@code AlterEgo.bind(...)}. */
final class DefaultTransformation<T> implements Transformation<T> {

  private final byte[] salt;
  private final Locale locale;
  private final String domain;
  private final Class<T> type;
  private final Strategy<T> strategy;
  private final MappingStore mappingStore;
  private final NullPolicy nullPolicy;

  DefaultTransformation(
      byte[] salt,
      Locale locale,
      String domain,
      Class<T> type,
      Strategy<T> strategy,
      MappingStore mappingStore,
      NullPolicy nullPolicy) {
    DomainNames.requireValid(domain, "domain");
    ValueCodecs.requireSupported(type);
    this.salt = salt;
    this.locale = locale;
    this.domain = domain;
    this.type = type;
    this.strategy = strategy;
    this.mappingStore = mappingStore;
    this.nullPolicy = nullPolicy;
  }

  @Override
  public T apply(T input) {
    if (input == null) {
      if (nullPolicy == NullPolicy.FAIL) {
        throw new AlterEgoException("Null input is not allowed under NullPolicy.FAIL: " + domain);
      }
      return null;
    }
    String canonical = ValueCodecs.encode(input, type);
    TransformationContext context =
        DefaultTransformationContext.topLevel(salt, locale, domain, canonical, mappingStore);
    return strategy.transform(input, context);
  }

  @Override
  public Transformation<T> unique() {
    throw uniqueOrStoredException();
  }

  @Override
  public Transformation<T> stored() {
    throw uniqueOrStoredException();
  }

  private AlterEgoStoreException uniqueOrStoredException() {
    if (mappingStore == null) {
      return new AlterEgoStoreException(
          "unique()/stored() require a configured MappingStore (domain: " + domain + ")");
    }
    return new AlterEgoStoreException(
        "unique()/stored() are not implemented until M4 (domain: " + domain + ")");
  }
}
