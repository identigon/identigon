package io.github.dconneely.alterego;

import io.github.dconneely.alterego.pattern.PatternStrategy;
import io.github.dconneely.alterego.store.MappingStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * The configured entry point for deterministic pseudonymisation: carries a secret salt, a
 * locale, and an optional mapping store, and hands out {@link Transformation}s and
 * {@link RecordScope}s that share that configuration. Immutable and thread-safe once built.
 */
public final class AlterEgo {

  private static final int MIN_SALT_BYTES = 16;

  private final byte[] salt;
  private final Locale locale;
  private final MappingStore mappingStore;
  private final NullPolicy nullPolicy;

  private AlterEgo(byte[] salt, Locale locale, MappingStore mappingStore, NullPolicy nullPolicy) {
    this.salt = salt;
    this.locale = locale;
    this.mappingStore = mappingStore;
    this.nullPolicy = nullPolicy;
  }

  /** Starts building a new {@code AlterEgo} instance. */
  public static Builder builder() {
    return new Builder();
  }

  /** Binds a {@code String} strategy under {@code domain} to a reusable transformation. */
  public Transformation<String> bind(String domain, Strategy<String> strategy) {
    return new DefaultTransformation<>(salt, locale, domain, String.class, strategy, mappingStore, nullPolicy);
  }

  /**
   * Binds a strategy over a supported non-{@code String} value type (section 2.6) under
   * {@code domain} to a reusable transformation.
   */
  public <T> Transformation<T> bind(String domain, Class<T> type, Strategy<T> strategy) {
    return new DefaultTransformation<>(salt, locale, domain, type, strategy, mappingStore, nullPolicy);
  }

  /**
   * Compiles {@code pattern} (section 4.6: {@code D}/{@code L}/{@code l}/{@code A}, {@code \}
   * escapes, literals) into a transformation whose output always matches the pattern's shape.
   * Compiled once, here, not per element; malformed patterns throw
   * {@link AlterEgoPatternException} immediately. Carries no fictionality guarantee (section
   * 4.1) — use a specific built-in such as {@code postcode()} when that guarantee matters.
   */
  public Transformation<String> pattern(String pattern) {
    Strategy<String> strategy = PatternStrategy.compile(pattern);
    return bind(patternDomain(pattern), strategy);
  }

  private static String patternDomain(String pattern) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(pattern.getBytes(StandardCharsets.UTF_8));
      return "alterego:pattern:" + HexFormat.of().formatHex(hash, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** Replaces every input with the fixed {@code value} (section 4.7). */
  public <T> Transformation<T> constant(T value) {
    Objects.requireNonNull(value, "value");
    Class<T> type = inferType(value);
    return bind("alterego:constant", type, (in, ctx) -> value);
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> inferType(T value) {
    if (value instanceof Enum<?> enumValue) {
      return (Class<T>) enumValue.getDeclaringClass();
    }
    return (Class<T>) value.getClass();
  }

  /**
   * Masks all but the last {@code keepLast} characters of each input with {@code maskChar}
   * (section 4.7). Inputs no longer than {@code keepLast} are returned unchanged.
   */
  public Transformation<String> mask(char maskChar, int keepLast) {
    if (keepLast < 0) {
      throw new AlterEgoConfigException("keepLast must be >= 0, got: " + keepLast);
    }
    Strategy<String> strategy = (in, ctx) -> maskValue(in, maskChar, keepLast);
    return bind("alterego:mask", strategy);
  }

  private static String maskValue(String input, char maskChar, int keepLast) {
    if (input.length() <= keepLast) {
      return input;
    }
    int maskCount = input.length() - keepLast;
    StringBuilder sb = new StringBuilder(input.length());
    for (int i = 0; i < maskCount; i++) {
      sb.append(maskChar);
    }
    sb.append(input, maskCount, input.length());
    return sb.toString();
  }

  /** Opens an anonymous record scope: attributes resolve using the first-asking field's own randomness. */
  public RecordScope record() {
    throw new UnsupportedOperationException("M5");
  }

  /**
   * Opens a keyed record scope: {@code computeIfAbsent} attribute resolution is derived from
   * {@code key} and the attribute name, independent of field order.
   */
  public RecordScope record(String key) {
    throw new UnsupportedOperationException("M5");
  }

  /** Builds a configured {@link AlterEgo} instance. */
  public static final class Builder {

    private byte[] salt;
    private Locale locale = Locale.UK;
    private MappingStore mappingStore;
    private NullPolicy nullPolicy = NullPolicy.PASS_THROUGH;

    private Builder() {}

    /** Sets the secret salt. Required; must be at least 16 bytes. */
    public Builder salt(byte[] salt) {
      this.salt = salt == null ? null : salt.clone();
      return this;
    }

    /** Sets the secret salt, converted to bytes via UTF-8. Required; must be at least 16 bytes. */
    public Builder salt(char[] salt) {
      this.salt = salt == null ? null : Derivation.charsToUtf8(salt);
      return this;
    }

    /** Sets the locale. Defaults to the fixed constant {@link Locale#UK}. */
    public Builder locale(Locale locale) {
      this.locale = Objects.requireNonNull(locale, "locale");
      return this;
    }

    /** Sets the mapping store used by {@code stored()} and {@code unique()}. Optional. */
    public Builder mappingStore(MappingStore mappingStore) {
      this.mappingStore = mappingStore;
      return this;
    }

    /** Sets the null-handling policy. Defaults to {@link NullPolicy#PASS_THROUGH}. */
    public Builder nullPolicy(NullPolicy nullPolicy) {
      this.nullPolicy = Objects.requireNonNull(nullPolicy, "nullPolicy");
      return this;
    }

    /** Validates the configuration and builds the {@link AlterEgo} instance. */
    public AlterEgo build() {
      if (salt == null || salt.length < MIN_SALT_BYTES) {
        throw new AlterEgoConfigException(
            "salt is required and must be at least "
                + MIN_SALT_BYTES
                + " bytes, got: "
                + (salt == null ? "none" : salt.length + " bytes"));
      }
      return new AlterEgo(salt, locale, mappingStore, nullPolicy);
    }
  }
}
