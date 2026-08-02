package org.identigon.alterego;

import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.identigon.alterego.store.MappingStore;
import org.identigon.alterego.store.MappingStore.PutUniqueResult;

/** The concrete {@link Transformation} returned by {@code AlterEgo.bind(...)}. */
final class DefaultTransformation<T> implements Transformation<T> {

  /** Decorator state (section 2.5): {@code stored()}/{@code unique()} only ever raise it. */
  private enum Mode {
    NONE,
    STORED,
    UNIQUE
  }

  private final byte[] salt;
  private final Locale locale;
  private final String domain;
  private final Class<T> type;
  private final Strategy<T> strategy;
  private final MappingStore mappingStore;
  private final NullPolicy nullPolicy;
  private final boolean rawMappingKeys;
  private final int uniqueMaxAttempts;
  private final Mode mode;

  /** Reports whether the owning {@link AlterEgo} has been closed (its salt zeroed). */
  private final BooleanSupplier parentClosed;

  DefaultTransformation(
      byte[] salt,
      Locale locale,
      String domain,
      Class<T> type,
      Strategy<T> strategy,
      MappingStore mappingStore,
      NullPolicy nullPolicy,
      boolean rawMappingKeys,
      int uniqueMaxAttempts,
      BooleanSupplier parentClosed) {
    this(
        salt, locale, domain, type, strategy, mappingStore, nullPolicy, rawMappingKeys, uniqueMaxAttempts, Mode.NONE,
        parentClosed);
  }

  private DefaultTransformation(
      byte[] salt,
      Locale locale,
      String domain,
      Class<T> type,
      Strategy<T> strategy,
      MappingStore mappingStore,
      NullPolicy nullPolicy,
      boolean rawMappingKeys,
      int uniqueMaxAttempts,
      Mode mode,
      BooleanSupplier parentClosed) {
    DomainNames.requireValid(domain, "domain");
    ValueCodecs.requireSupported(type);
    this.salt = salt;
    this.locale = locale;
    this.domain = domain;
    this.type = type;
    this.strategy = strategy;
    this.mappingStore = mappingStore;
    this.nullPolicy = nullPolicy;
    this.rawMappingKeys = rawMappingKeys;
    this.uniqueMaxAttempts = uniqueMaxAttempts;
    this.mode = mode;
    this.parentClosed = parentClosed;
  }

  @Override
  public T apply(T input) {
    if (parentClosed.getAsBoolean()) {
      throw new IllegalStateException(
          "AlterEgo instance has been destroyed; its transformations can no longer be applied (domain: "
              + domain
              + ")");
    }
    if (input == null) {
      if (nullPolicy == NullPolicy.FAIL) {
        throw new AlterEgoException("Null input is not allowed under NullPolicy.FAIL: " + domain);
      }
      return null;
    }
    String canonical = ValueCodecs.encode(input, type);
    return switch (mode) {
      case NONE -> applyPlain(input, canonical);
      case STORED -> applyStored(input, canonical);
      case UNIQUE -> applyUnique(input, canonical);
    };
  }

  private T applyPlain(T input, String canonical) {
    TransformationContext context = topLevelContext(canonical);
    return strategy.transform(input, context);
  }

  private T applyStored(T input, String canonical) {
    String key = mapKey(canonical);
    Optional<String> existing = mappingStore.get(domain, key);
    if (existing.isPresent()) {
      return decode(existing.get());
    }
    T candidate = strategy.transform(input, topLevelContext(canonical));
    String stored = mappingStore.putIfAbsent(domain, key, ValueCodecs.encode(candidate, type));
    return decode(stored);
  }

  private T applyUnique(T input, String canonical) {
    String key = mapKey(canonical);
    Optional<String> existing = mappingStore.get(domain, key);
    if (existing.isPresent()) {
      return decode(existing.get());
    }
    for (int counter = 0; counter < uniqueMaxAttempts; counter++) {
      TransformationContext context =
          counter == 0
              ? topLevelContext(canonical)
              : DefaultTransformationContext.retry(salt, locale, domain, canonical, counter, mappingStore, rawMappingKeys);
      T candidate = strategy.transform(input, context);
      PutUniqueResult result = mappingStore.putIfAbsentUnique(domain, key, ValueCodecs.encode(candidate, type));
      switch (result) {
        case PutUniqueResult.Stored ignored -> {
          return candidate;
        }
        case PutUniqueResult.ExistingMapping(String value) -> {
          return decode(value);
        }
        case PutUniqueResult.ValueTaken ignored -> {
          // Bump the retry counter (Appendix A.1) and try again.
        }
      }
    }
    throw new AlterEgoCollisionException(
        "unique() exhausted "
            + uniqueMaxAttempts
            + " attempts for domain '"
            + domain
            + "' — the output space is likely too small for the input volume");
  }

  private TransformationContext topLevelContext(String canonical) {
    return DefaultTransformationContext.topLevel(salt, locale, domain, canonical, mappingStore, rawMappingKeys);
  }

  private String mapKey(String canonical) {
    return Derivation.mapKey(salt, domain, canonical, rawMappingKeys);
  }

  private T decode(String value) {
    return ValueCodecs.decode(value, type);
  }

  @Override
  public Transformation<T> unique() {
    requireMappingStore();
    return mode == Mode.UNIQUE ? this : withMode(Mode.UNIQUE);
  }

  @Override
  public Transformation<T> stored() {
    requireMappingStore();
    return mode == Mode.NONE ? withMode(Mode.STORED) : this;
  }

  private void requireMappingStore() {
    if (mappingStore == null) {
      throw new AlterEgoStoreException("unique()/stored() require a configured MappingStore (domain: " + domain + ")");
    }
  }

  private DefaultTransformation<T> withMode(Mode newMode) {
    return new DefaultTransformation<>(
        salt, locale, domain, type, strategy, mappingStore, nullPolicy, rawMappingKeys, uniqueMaxAttempts, newMode,
        parentClosed);
  }
}
