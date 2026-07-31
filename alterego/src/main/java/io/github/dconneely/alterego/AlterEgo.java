package io.github.dconneely.alterego;

import io.github.dconneely.alterego.pattern.PatternStrategy;
import io.github.dconneely.alterego.store.MappingStore;
import io.github.dconneely.alterego.strategy.CityStrategy;
import io.github.dconneely.alterego.strategy.DateJitterStrategy;
import io.github.dconneely.alterego.strategy.DateTimeJitterStrategy;
import io.github.dconneely.alterego.strategy.DictionaryLoader;
import io.github.dconneely.alterego.strategy.DomainNameStrategy;
import io.github.dconneely.alterego.strategy.EmailAddressStrategy;
import io.github.dconneely.alterego.strategy.FullNameStrategy;
import io.github.dconneely.alterego.strategy.InstantJitterStrategy;
import io.github.dconneely.alterego.strategy.NameDictionaryStrategy;
import io.github.dconneely.alterego.strategy.NationalInsuranceNumberStrategy;
import io.github.dconneely.alterego.strategy.NhsNumberStrategy;
import io.github.dconneely.alterego.strategy.OrganisationNameStrategy;
import io.github.dconneely.alterego.strategy.PassportNumberStrategy;
import io.github.dconneely.alterego.strategy.PhoneNumberStrategy;
import io.github.dconneely.alterego.strategy.PostcodeStrategy;
import io.github.dconneely.alterego.strategy.StreetAddressStrategy;
import io.github.dconneely.alterego.strategy.UrlStrategy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * The configured entry point for deterministic pseudonymisation: carries a secret salt, a
 * locale, and an optional mapping store, and hands out {@link Transformation}s and
 * {@link RecordScope}s that share that configuration. Immutable and thread-safe once built.
 */
public final class AlterEgo implements AutoCloseable {

  private static final int MIN_SALT_BYTES = 16;

  private final byte[] salt;
  private final Locale locale;
  private final MappingStore mappingStore;
  private final NullPolicy nullPolicy;
  private final boolean rawMappingKeys;
  private final int uniqueMaxAttempts;
  private volatile boolean closed;

  private AlterEgo(
      byte[] salt,
      Locale locale,
      MappingStore mappingStore,
      NullPolicy nullPolicy,
      boolean rawMappingKeys,
      int uniqueMaxAttempts) {
    this.salt = salt;
    this.locale = locale;
    this.mappingStore = mappingStore;
    this.nullPolicy = nullPolicy;
    this.rawMappingKeys = rawMappingKeys;
    this.uniqueMaxAttempts = uniqueMaxAttempts;
  }

  /**
   * Starts building a new {@code AlterEgo} instance.
   *
   * @return a new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Binds a {@code String} strategy under {@code domain} to a reusable transformation.
   *
   * @param domain the transformation's namespace (section 2.6)
   * @param strategy the transformation logic
   * @return a reusable {@link Transformation}
   */
  public Transformation<String> bind(String domain, Strategy<String> strategy) {
    checkNotClosed();
    return new DefaultTransformation<>(
        salt, locale, domain, String.class, strategy, mappingStore, nullPolicy, rawMappingKeys, uniqueMaxAttempts,
        this::isClosed);
  }

  /**
   * Binds a strategy over a supported non-{@code String} value type (section 2.6) under
   * {@code domain} to a reusable transformation.
   *
   * @param <T> the value type transformed
   * @param domain the transformation's namespace (section 2.6)
   * @param type the value type, one of the supported types in section 2.6
   * @param strategy the transformation logic
   * @return a reusable {@link Transformation}
   */
  public <T> Transformation<T> bind(String domain, Class<T> type, Strategy<T> strategy) {
    checkNotClosed();
    return new DefaultTransformation<>(
        salt, locale, domain, type, strategy, mappingStore, nullPolicy, rawMappingKeys, uniqueMaxAttempts,
        this::isClosed);
  }

  /**
   * Compiles {@code pattern} (section 4.6: {@code D}/{@code L}/{@code l}/{@code A}, {@code \}
   * escapes, literals) into a transformation whose output always matches the pattern's shape.
   * Compiled once, here, not per element; malformed patterns throw
   * {@link AlterEgoPatternException} immediately. Carries no fictionality guarantee (section
   * 4.1) — use a specific built-in such as {@code postcode()} when that guarantee matters.
   *
   * @param pattern the pattern text (section 4.6)
   * @return a {@link Transformation} whose output always matches {@code pattern}'s shape
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

  /**
   * Replaces every input with the fixed {@code value} (section 4.7).
   *
   * @param <T> the value type transformed
   * @param value the fixed replacement value
   * @return a {@link Transformation} that always returns {@code value}
   */
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
   * Returns a {@link Transformation} that replaces every input with a sensible dummy constant
   * for the given type (e.g. {@code 0} for {@link Integer}, {@code 1970-01-01} for {@link java.time.LocalDate}).
   * Throws {@link AlterEgoConfigException} if there is no obvious safe default for the type (e.g. Enums).
   *
   * @param <T> the value type transformed
   * @param type the class of the value type
   * @return a {@link Transformation} that always returns the default redacted value
   */
  @SuppressWarnings("unchecked")
  public <T> Transformation<T> redact(Class<T> type) {
    Objects.requireNonNull(type, "type");
    if (type == String.class) {
      return (Transformation<T>) constant("");
    } else if (type == Integer.class) {
      return (Transformation<T>) constant(0);
    } else if (type == Long.class) {
      return (Transformation<T>) constant(0L);
    } else if (type == Boolean.class) {
      return (Transformation<T>) constant(Boolean.FALSE);
    } else if (type == LocalDate.class) {
      return (Transformation<T>) constant(LocalDate.of(1970, 1, 1));
    } else if (type == LocalDateTime.class) {
      return (Transformation<T>) constant(LocalDateTime.of(1970, 1, 1, 0, 0));
    } else if (type == java.time.Instant.class) {
      return (Transformation<T>) constant(java.time.Instant.EPOCH);
    } else if (type == java.time.LocalTime.class) {
      return (Transformation<T>) constant(java.time.LocalTime.MIDNIGHT);
    } else if (type == java.time.YearMonth.class) {
      return (Transformation<T>) constant(java.time.YearMonth.of(1970, 1));
    } else if (type == java.math.BigDecimal.class) {
      return (Transformation<T>) constant(java.math.BigDecimal.ZERO);
    } else if (type == java.util.UUID.class) {
      return (Transformation<T>) constant(new java.util.UUID(0L, 0L));
    }
    throw new AlterEgoConfigException(
        "Cannot provide a default redacted constant for type: " + type.getName() + " (use constant(T) instead)");
  }

  /**
   * Masks every character of each input with {@code maskChar} (section 4.7).
   *
   * @param maskChar the character used to mask each position
   * @return a masking {@link Transformation}
   */
  public Transformation<String> mask(char maskChar) {
    return mask(maskChar, 0);
  }

  /**
   * Masks all but the last {@code keepLast} characters of each input with {@code maskChar}
   * (section 4.7). Inputs no longer than {@code keepLast} are returned unchanged.
   *
   * @param maskChar the character used to mask each replaced position
   * @param keepLast how many trailing characters to leave unmasked; must be {@code >= 0}
   * @return a masking {@link Transformation}
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

  /**
   * Replacement drawn from the locale's country's first-name dictionary (section 4.2).
   *
   * @return a {@link Transformation} over first names
   */
  public Transformation<String> firstName() {
    return firstName(NameOptions.defaults());
  }

  /**
   * As {@link #firstName()}, with {@link NameOptions}.
   *
   * @param options first-name generation options
   * @return a {@link Transformation} over first names
   */
  public Transformation<String> firstName(NameOptions options) {
    String country = DictionaryLoader.requireCountry(locale);
    Strategy<String> strategy =
        NameDictionaryStrategy.forDictionary(country, "first-names", options.isPreserveInitial());
    return bind(FullNameStrategy.FIRST_NAME_DOMAIN, strategy);
  }

  /**
   * Replacement drawn from the locale's country's surname dictionary (section 4.2). The surname
   * vocabulary is authored to read as obviously fictional (section 4.1) — never a real person's
   * surname.
   *
   * @return a {@link Transformation} over surnames
   */
  public Transformation<String> lastName() {
    return lastName(NameOptions.defaults());
  }

  /**
   * As {@link #lastName()}, with {@link NameOptions}.
   *
   * @param options surname generation options
   * @return a {@link Transformation} over surnames
   */
  public Transformation<String> lastName(NameOptions options) {
    String country = DictionaryLoader.requireCountry(locale);
    Strategy<String> strategy =
        NameDictionaryStrategy.forDictionary(country, "surnames", options.isPreserveInitial());
    return bind(FullNameStrategy.LAST_NAME_DOMAIN, strategy);
  }

  /**
   * Tokenises and delegates to {@link #firstName()}/{@link #lastName()} strategies per the
   * pinned rules of section 4.2, so results agree with those standalone transformations.
   *
   * @return a {@link Transformation} over full names
   */
  public Transformation<String> fullName() {
    String country = DictionaryLoader.requireCountry(locale);
    Strategy<String> strategy = FullNameStrategy.forCountry(country);
    return bind("alterego:full-name", strategy);
  }

  /**
   * Replacement drawn from the locale's country's town/city dictionary (section 4.3). Inside a
   * record scope, coheres with {@code postcode()}/{@code phoneNumber()} via {@code
   * UK_POSTCODE_AREA}/{@code UK_NATION} (section 6.3).
   *
   * @return a {@link Transformation} over towns/cities
   */
  public Transformation<String> city() {
    String country = DictionaryLoader.requireCountry(locale);
    Strategy<String> strategy = CityStrategy.forCountry(country);
    return bind("alterego:city", strategy);
  }

  /**
   * A house number (1-299) plus a complete street name composed from the locale's country's
   * street dictionaries (section 4.3). Theme words are authored to read as obviously fictional
   * (section 4.1) — never a real street name; type words ("Road", "Avenue") are real structural
   * vocabulary.
   *
   * @return a {@link Transformation} over street addresses
   */
  public Transformation<String> streetAddress() {
    String country = DictionaryLoader.requireCountry(locale);
    Strategy<String> strategy = StreetAddressStrategy.forCountry(country);
    return bind("alterego:street-address", strategy);
  }

  /**
   * Country-specific postcode format with the fictionality guarantee of section 4.1 where one
   * is defined (e.g. for UK, the inward code ends in a letter never used).
   *
   * @return a {@link Transformation} over postcodes
   */
  public Transformation<String> postcode() {
    return postcode(PostcodeOptions.defaults());
  }

  /**
   * As {@link #postcode()}, with {@link PostcodeOptions}.
   *
   * @param options postcode generation options
   * @return a {@link Transformation} over postcodes
   */
  public Transformation<String> postcode(PostcodeOptions options) {
    String country = DictionaryLoader.requireCountry(locale);
    Strategy<String> strategy = PostcodeStrategy.forCountry(country, options.isRealistic());
    return bind("alterego:postcode", strategy);
  }

  /**
   * Generated from the locale's country's organisation-name component list, preserving a
   * recognised legal suffix from the input if present (section 4.2).
   *
   * @return a {@link Transformation} over organisation names
   */
  public Transformation<String> organisationName() {
    String country = DictionaryLoader.requireCountry(locale);
    Strategy<String> strategy = OrganisationNameStrategy.forCountry(country);
    return bind("alterego:organisation-name", strategy);
  }

  // --- Contact details (section 4.4) --------------------------------------------------------

  /**
   * Generates a fictional email address (section 4.1, section 4.4): splits at the last {@code
   * @}, replaces the local part class-wise, and by default draws the domain from the RFC 2606
   * reserved set (guaranteed non-working).
   *
   * @return a {@link Transformation} over email addresses
   */
  public Transformation<String> emailAddress() {
    return emailAddress(EmailOptions.defaults());
  }

  /**
   * As {@link #emailAddress()}, with {@link EmailOptions}.
   *
   * @param options email generation options
   * @return a {@link Transformation} over email addresses
   */
  public Transformation<String> emailAddress(EmailOptions options) {
    Strategy<String> strategy = EmailAddressStrategy.create(options.isPreserveDomain(), options.mappedDomain());
    return bind("alterego:email-address", strategy);
  }

  /**
   * Returns a {@link Transformation} that derives a fictional domain name for each input.
   *
   * @return a fictional-by-default domain name transformation
   */
  public Transformation<String> domainName() {
    return bind("alterego:domain-name", DomainNameStrategy.INSTANCE);
  }

  /**
   * Returns a {@link Transformation} that derives a fictional URL for each input.
   *
   * @return a fictional-by-default URL transformation
   */
  public Transformation<String> url() {
    return bind("alterego:url", UrlStrategy.INSTANCE);
  }

  /**
   * Generates a fictional phone number (section 4.1, section 4.4): digits replaced in place,
   * punctuation and grouping preserved. By default lands in the locale's country's reserved
   * fictional range where one is published ({@code docs/phone-ranges.md}); a country with no
   * range table falls back to plain digit replacement, with no fictionality guarantee.
   *
   * @return a {@link Transformation} over phone numbers
   */
  public Transformation<String> phoneNumber() {
    return phoneNumber(PhoneOptions.defaults());
  }

  /**
   * As {@link #phoneNumber()}, with {@link PhoneOptions}.
   *
   * @param options phone number generation options
   * @return a {@link Transformation} over phone numbers
   */
  public Transformation<String> phoneNumber(PhoneOptions options) {
    String country = DictionaryLoader.requireCountry(locale);
    Strategy<String> strategy = PhoneNumberStrategy.forCountry(country, options.isRealistic(), options.isIncludeNonGeographic());
    return bind("alterego:phone-number", strategy);
  }

  /**
   * Returns a {@link Transformation} that shifts each input {@link Instant} uniformly.
   *
   * @param days the half-range of the date shift, in days; must be {@code >= 0}
   * @param seconds the half-range of the time shift, in seconds; must be {@code >= 0}
   * @return a date-and-time jitter transformation over {@link Instant}
   */
  public Transformation<Instant> shiftInstant(int days, int seconds) {
    Strategy<Instant> strategy = InstantJitterStrategy.of(days, seconds);
    return bind(shiftInstantDomain(dateFragment(days), timeFragment(seconds)), Instant.class, strategy);
  }

  /**
   * As {@link #shiftInstant(int, int)}, clamped inclusively by {@code options} after shifting.
   *
   * @param days the half-range of the date shift, in days; must be {@code >= 0}
   * @param seconds the half-range of the time shift, in seconds; must be {@code >= 0}
   * @param options inclusive clamp bounds to apply after shifting
   * @return a clamped date-and-time jitter transformation over {@link Instant}
   */
  public Transformation<Instant> shiftInstant(int days, int seconds, JitterOptions<Instant> options) {
    Strategy<Instant> strategy = clampInstant(InstantJitterStrategy.of(days, seconds), options);
    return bind(shiftInstantDomain(dateFragment(days), timeFragment(seconds)), Instant.class, strategy);
  }

  // --- Internals --------------------------------------------------------------------------------

  // --- Identifiers (section 4.8) ------------------------------------------------------------

  /**
   * Generates a fictional NHS number (SPECIFICATION.md section 4.1, 4.8).
   * <p>
   * Output format: 10 digits with 3-3-4 spacing, e.g. "999 ddd dddc", where c is a valid mod-11 check digit.
   * Guarantee: The number falls within the '999' test/synthetic range and is never issued to a real person.
   * Requires the locale's country to be GB.
   *
   * @return a {@link Transformation} over NHS numbers
   */
  public Transformation<String> nhsNumber() {
    requireGB("nhsNumber");
    return bind("alterego:nhs-number", io.github.dconneely.alterego.strategy.NhsNumberStrategy.INSTANCE);
  }

  /**
   * Generates a fictional National Insurance number (SPECIFICATION.md section 4.1, 4.8).
   * <p>
   * Output format: "QQ dd dd dd S".
   * Guarantee: The 'QQ' prefix is structurally unallocatable by HMRC (used only for examples).
   * Requires the locale's country to be GB.
   *
   * @return a {@link Transformation} over National Insurance numbers
   */
  public Transformation<String> nationalInsuranceNumber() {
    requireGB("nationalInsuranceNumber");
    return bind("alterego:national-insurance-number", io.github.dconneely.alterego.strategy.NationalInsuranceNumberStrategy.INSTANCE);
  }

  /**
   * Generates a fictional GB driving licence number (SPECIFICATION.md section 4.1, 4.8).
   * <p>
   * Output format: 16 characters DVLA layout, unspaced.
   * Guarantee: Uses the surname block "99999", which implies a zero-letter surname and can never occur on a real licence.
   * Requires the locale's country to be GB.
   *
   * @return a {@link Transformation} over GB driving licence numbers
   */
  public Transformation<String> drivingLicenceNumber() {
    requireGB("drivingLicenceNumber");
    return bind("alterego:driving-licence-number", io.github.dconneely.alterego.strategy.DrivingLicenceNumberStrategy.INSTANCE);
  }

  /**
   * Generates a fictional UK passport number (SPECIFICATION.md section 4.1, 4.8).
   * <p>
   * Output format: "ZZddddddd", unspaced.
   * Guarantee: The "ZZ" prefix makes it structurally impossible for a UK passport, which must be 9 numeric digits.
   * Requires the locale's country to be GB.
   *
   * @return a {@link Transformation} over UK passport numbers
   */
  public Transformation<String> passportNumber() {
    requireGB("passportNumber");
    return bind("alterego:passport-number", io.github.dconneely.alterego.strategy.PassportNumberStrategy.INSTANCE);
  }

  /**
   * Generates a fictional credit card number (SPECIFICATION.md section 4.1, 4.8).
   * <p>
   * Output format: 16 digits grouped as "0ddd dddd dddd dddc" with a valid Luhn check digit.
   * Guarantee: The '0' major industry identifier is reserved for ISO/TC 68 and future assignment; no scheme issues PANs beginning with 0.
   *
   * @return a {@link Transformation} over credit card numbers
   */
  public Transformation<String> creditCardNumber() {
    return bind("alterego:credit-card-number", io.github.dconneely.alterego.strategy.CreditCardNumberStrategy.INSTANCE);
  }

  private void requireGB(String methodName) {
    String country = DictionaryLoader.requireCountry(locale);
    if (!"GB".equals(country)) {
      throw new AlterEgoConfigException(methodName + " requires country GB, but locale " + locale + " resolved to " + country);
    }
  }

  // --- Temporal jitter (section 4.5) --------------------------------------------------------

  /**
   * Whole-day shift, uniform over {@code [-days, +days]} (Appendix A.3). {@code days} must be >= 0.
   *
   * @param days the half-range of the shift, in days
   * @return a {@link Transformation} over dates
   */
  public Transformation<LocalDate> shiftDate(int days) {
    Strategy<LocalDate> strategy = DateJitterStrategy.byDays(days);
    return bind(shiftDateDomain(dateFragment(days)), LocalDate.class, strategy);
  }

  /**
   * As {@link #shiftDate(int)}, clamped inclusively by {@code options} after shifting.
   *
   * @param days the half-range of the shift, in days
   * @param options the inclusive clamp bounds
   * @return a {@link Transformation} over dates
   */
  public Transformation<LocalDate> shiftDate(int days, JitterOptions<LocalDate> options) {
    Objects.requireNonNull(options, "options");
    Strategy<LocalDate> strategy = clampDate(DateJitterStrategy.byDays(days), options);
    return bind(shiftDateDomain(dateFragment(days)), LocalDate.class, strategy);
  }

  /**
   * {@code MONTH}: uniform random day within the input's own year and month. {@code YEAR}:
   * uniform random day within the input's own year, leap-aware.
   *
   * @param field which date-jitter strategy to run
   * @return a {@link Transformation} over dates
   */
  public Transformation<LocalDate> shiftDate(DateField field) {
    Strategy<LocalDate> strategy = DateJitterStrategy.byField(field);
    return bind(shiftDateDomain(dateFragment(field)), LocalDate.class, strategy);
  }

  /**
   * As {@link #shiftDate(AlterEgo.DateField)}, clamped inclusively by {@code options} after shifting.
   *
   * @param field which date-jitter strategy to run
   * @param options the inclusive clamp bounds
   * @return a {@link Transformation} over dates
   */
  public Transformation<LocalDate> shiftDate(DateField field, JitterOptions<LocalDate> options) {
    Objects.requireNonNull(options, "options");
    Strategy<LocalDate> strategy = clampDate(DateJitterStrategy.byField(field), options);
    return bind(shiftDateDomain(dateFragment(field)), LocalDate.class, strategy);
  }

  /**
   * Pairs the {@link #shiftDate(int)} date strategy with a whole-second shift uniform over
   * {@code [-seconds, +seconds]}. Nanoseconds are zeroed in the output.
   *
   * @param days the half-range of the date shift, in days
   * @param seconds the half-range of the time shift, in seconds
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(int days, int seconds) {
    Strategy<LocalDateTime> strategy = DateTimeJitterStrategy.of(days, seconds);
    return bind(shiftDateTimeDomain(dateFragment(days), timeFragment(seconds)), LocalDateTime.class, strategy);
  }

  /**
   * As {@link #shiftDateTime(int, int)}, clamped inclusively by {@code options} after shifting.
   *
   * @param days the half-range of the date shift, in days
   * @param seconds the half-range of the time shift, in seconds
   * @param options the inclusive clamp bounds
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(int days, int seconds, JitterOptions<LocalDateTime> options) {
    Objects.requireNonNull(options, "options");
    Strategy<LocalDateTime> strategy = clampDateTime(DateTimeJitterStrategy.of(days, seconds), options);
    return bind(shiftDateTimeDomain(dateFragment(days), timeFragment(seconds)), LocalDateTime.class, strategy);
  }

  /**
   * Pairs the {@link #shiftDate(int)} date strategy with a uniform random point in
   * {@code [start, end]} inclusive, to the second. {@code start} after {@code end} throws
   * {@link AlterEgoConfigException} immediately.
   *
   * @param days the half-range of the date shift, in days
   * @param start the inclusive lower bound of the time-of-day range
   * @param end the inclusive upper bound of the time-of-day range
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(int days, LocalTime start, LocalTime end) {
    Strategy<LocalDateTime> strategy = DateTimeJitterStrategy.of(days, start, end);
    return bind(
        shiftDateTimeDomain(dateFragment(days), timeFragment(start, end)), LocalDateTime.class, strategy);
  }

  /**
   * As {@link #shiftDateTime(int, LocalTime, LocalTime)}, clamped inclusively by {@code options} after shifting.
   *
   * @param days the half-range of the date shift, in days
   * @param start the inclusive lower bound of the time-of-day range
   * @param end the inclusive upper bound of the time-of-day range
   * @param options the inclusive clamp bounds
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(
      int days, LocalTime start, LocalTime end, JitterOptions<LocalDateTime> options) {
    Objects.requireNonNull(options, "options");
    Strategy<LocalDateTime> strategy = clampDateTime(DateTimeJitterStrategy.of(days, start, end), options);
    return bind(
        shiftDateTimeDomain(dateFragment(days), timeFragment(start, end)), LocalDateTime.class, strategy);
  }

  /**
   * Pairs the {@link #shiftDate(int)} date strategy with the same hour as the input and a
   * uniform random minute, then second.
   *
   * @param days the half-range of the date shift, in days
   * @param field which time-jitter strategy to run
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(int days, TimeField field) {
    Strategy<LocalDateTime> strategy = DateTimeJitterStrategy.of(days, field);
    return bind(shiftDateTimeDomain(dateFragment(days), timeFragment(field)), LocalDateTime.class, strategy);
  }

  /**
   * As {@link #shiftDateTime(int, AlterEgo.TimeField)}, clamped inclusively by {@code options} after shifting.
   *
   * @param days the half-range of the date shift, in days
   * @param field which time-jitter strategy to run
   * @param options the inclusive clamp bounds
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(int days, TimeField field, JitterOptions<LocalDateTime> options) {
    Objects.requireNonNull(options, "options");
    Strategy<LocalDateTime> strategy = clampDateTime(DateTimeJitterStrategy.of(days, field), options);
    return bind(shiftDateTimeDomain(dateFragment(days), timeFragment(field)), LocalDateTime.class, strategy);
  }

  /**
   * Pairs the {@link #shiftDate(AlterEgo.DateField)} date strategy with a whole-second shift
   * uniform over {@code [-seconds, +seconds]}. Nanoseconds are zeroed in the output.
   *
   * @param dateField which date-jitter strategy to run
   * @param seconds the half-range of the time shift, in seconds
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(DateField dateField, int seconds) {
    Strategy<LocalDateTime> strategy = DateTimeJitterStrategy.of(dateField, seconds);
    return bind(
        shiftDateTimeDomain(dateFragment(dateField), timeFragment(seconds)), LocalDateTime.class, strategy);
  }

  /**
   * As {@link #shiftDateTime(AlterEgo.DateField, int)}, clamped inclusively by {@code options} after shifting.
   *
   * @param dateField which date-jitter strategy to run
   * @param seconds the half-range of the time shift, in seconds
   * @param options the inclusive clamp bounds
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(
      DateField dateField, int seconds, JitterOptions<LocalDateTime> options) {
    Objects.requireNonNull(options, "options");
    Strategy<LocalDateTime> strategy = clampDateTime(DateTimeJitterStrategy.of(dateField, seconds), options);
    return bind(
        shiftDateTimeDomain(dateFragment(dateField), timeFragment(seconds)), LocalDateTime.class, strategy);
  }

  /**
   * Pairs the {@link #shiftDate(AlterEgo.DateField)} date strategy with a uniform random point
   * in {@code [start, end]} inclusive, to the second. {@code start} after {@code end} throws
   * {@link AlterEgoConfigException} immediately.
   *
   * @param dateField which date-jitter strategy to run
   * @param start the inclusive lower bound of the time-of-day range
   * @param end the inclusive upper bound of the time-of-day range
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(DateField dateField, LocalTime start, LocalTime end) {
    Strategy<LocalDateTime> strategy = DateTimeJitterStrategy.of(dateField, start, end);
    return bind(
        shiftDateTimeDomain(dateFragment(dateField), timeFragment(start, end)),
        LocalDateTime.class,
        strategy);
  }

  /**
   * As {@link #shiftDateTime(AlterEgo.DateField, LocalTime, LocalTime)}, clamped inclusively by
   * {@code options} after shifting.
   *
   * @param dateField which date-jitter strategy to run
   * @param start the inclusive lower bound of the time-of-day range
   * @param end the inclusive upper bound of the time-of-day range
   * @param options the inclusive clamp bounds
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(
      DateField dateField, LocalTime start, LocalTime end, JitterOptions<LocalDateTime> options) {
    Objects.requireNonNull(options, "options");
    Strategy<LocalDateTime> strategy = clampDateTime(DateTimeJitterStrategy.of(dateField, start, end), options);
    return bind(
        shiftDateTimeDomain(dateFragment(dateField), timeFragment(start, end)),
        LocalDateTime.class,
        strategy);
  }

  /**
   * Pairs the {@link #shiftDate(AlterEgo.DateField)} date strategy with the same hour as the
   * input and a uniform random minute, then second.
   *
   * @param dateField which date-jitter strategy to run
   * @param timeField which time-jitter strategy to run
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(DateField dateField, TimeField timeField) {
    Strategy<LocalDateTime> strategy = DateTimeJitterStrategy.of(dateField, timeField);
    return bind(
        shiftDateTimeDomain(dateFragment(dateField), timeFragment(timeField)),
        LocalDateTime.class,
        strategy);
  }

  /**
   * As {@link #shiftDateTime(AlterEgo.DateField, AlterEgo.TimeField)}, clamped inclusively by
   * {@code options} after shifting.
   *
   * @param dateField which date-jitter strategy to run
   * @param timeField which time-jitter strategy to run
   * @param options the inclusive clamp bounds
   * @return a {@link Transformation} over date-times
   */
  public Transformation<LocalDateTime> shiftDateTime(
      DateField dateField, TimeField timeField, JitterOptions<LocalDateTime> options) {
    Objects.requireNonNull(options, "options");
    Strategy<LocalDateTime> strategy = clampDateTime(DateTimeJitterStrategy.of(dateField, timeField), options);
    return bind(
        shiftDateTimeDomain(dateFragment(dateField), timeFragment(timeField)),
        LocalDateTime.class,
        strategy);
  }

  private static Strategy<LocalDate> clampDate(Strategy<LocalDate> strategy, JitterOptions<LocalDate> options) {
    return (input, context) -> options.clamp(strategy.transform(input, context));
  }

  private static Strategy<LocalDateTime> clampDateTime(
      Strategy<LocalDateTime> strategy, JitterOptions<LocalDateTime> options) {
    return (input, context) -> options.clamp(strategy.transform(input, context));
  }

  private static Strategy<Instant> clampInstant(
      Strategy<Instant> strategy, JitterOptions<Instant> options) {
    return (input, context) -> options.clamp(strategy.transform(input, context));
  }

  private static String shiftDateDomain(String dateFragment) {
    return "alterego:shift-date:" + dateFragment;
  }

  private static String shiftDateTimeDomain(String dateFragment, String timeFragment) {
    return "alterego:shift-date-time:" + dateFragment + ":" + timeFragment;
  }

  private static String shiftInstantDomain(String dateFragment, String timeFragment) {
    return "alterego:shift-instant:" + dateFragment + ":" + timeFragment;
  }

  private static String dateFragment(int days) {
    return "days-" + days;
  }

  private static String dateFragment(DateField field) {
    return field.name().toLowerCase(Locale.ROOT);
  }

  private static String timeFragment(int seconds) {
    return "seconds-" + seconds;
  }

  private static String timeFragment(LocalTime start, LocalTime end) {
    return "range-" + start + "-" + end;
  }

  private static String timeFragment(TimeField field) {
    return field.name().toLowerCase(Locale.ROOT);
  }

  /**
   * Opens an anonymous record scope: attributes resolve using the first-asking field's own randomness.
   *
   * @return a new {@link RecordScope}
   */
  public RecordScope record() {
    checkNotClosed();
    return new DefaultRecordScope(salt, null);
  }

  /**
   * Opens a keyed record scope: {@code computeIfAbsent} attribute resolution is derived from
   * {@code key} and the attribute name, independent of field order.
   *
   * @param key the record's own key, e.g. a case or row identifier
   * @return a new {@link RecordScope}
   */
  public RecordScope record(String key) {
    Objects.requireNonNull(key, "key");
    checkNotClosed();
    return new DefaultRecordScope(salt, key);
  }

  /**
   * Selects which date-jitter strategy a {@code shiftDate}/{@code shiftDateTime} call runs
   * (section 4.5). Kept separate from {@link TimeField} so an invalid combination (e.g.
   * {@code HOUR} where a date strategy is expected) is a compile error, not a runtime check.
   */
  public enum DateField {
    /** Uniform random day within the input's own month (Appendix A.3, {@code nextInt(lengthOfMonth)}). */
    MONTH,
    /** Uniform random day within the input's own year, leap-aware ({@code nextInt(lengthOfYear)}). */
    YEAR
  }

  /**
   * Selects which time-jitter strategy the time part of a {@code shiftDateTime} call runs
   * (section 4.5). Kept separate from {@link DateField} for the same reason.
   */
  public enum TimeField {
    /** Same hour as the input; uniform random minute, then second, each {@code nextInt(60)}. */
    HOUR
  }

  /**
   * Destroys this instance by zeroing out the secret salt array.
   */
  public void destroy() {
    close();
  }

  /**
   * Destroys this instance by zeroing out the secret salt array. After calling this method,
   * factory methods on this instance will throw {@link IllegalStateException}.
   */
  @Override
  public void close() {
    closed = true;
    java.util.Arrays.fill(salt, (byte) 0);
  }

  private void checkNotClosed() {
    if (closed) {
      throw new IllegalStateException("AlterEgo instance has been destroyed");
    }
  }

  /**
   * Whether this instance has been closed. Shared with every {@link Transformation} it produces so
   * they can reject application after the salt has been zeroed (see {@link #close()}).
   */
  boolean isClosed() {
    return closed;
  }

  /** Builds a configured {@link AlterEgo} instance. */
  public static final class Builder {

    private static final int DEFAULT_UNIQUE_MAX_ATTEMPTS = 64;

    private byte[] salt;
    private Locale locale = Locale.UK;
    private MappingStore mappingStore;
    private NullPolicy nullPolicy = NullPolicy.PASS_THROUGH;
    private boolean rawMappingKeys = false;
    private int uniqueMaxAttempts = DEFAULT_UNIQUE_MAX_ATTEMPTS;

    private Builder() {}

    /**
     * Sets the secret salt. Required; must be at least 16 bytes.
     *
     * @param salt the secret salt bytes, defensively copied
     * @return this builder
     */
    public Builder salt(byte[] salt) {
      this.salt = salt == null ? null : salt.clone();
      return this;
    }

    /**
     * Sets the secret salt, converted to bytes via UTF-8. Required; must be at least 16 bytes.
     *
     * @param salt the secret salt characters
     * @return this builder
     */
    public Builder salt(char[] salt) {
      this.salt = salt == null ? null : Derivation.charsToUtf8(salt);
      return this;
    }

    /**
     * Sets the locale. Defaults to the fixed constant {@link Locale#UK}.
     *
     * @param locale the locale built-in transformations resolve country-specific resources by
     * @return this builder
     */
    public Builder locale(Locale locale) {
      this.locale = Objects.requireNonNull(locale, "locale");
      return this;
    }

    /**
     * Sets the mapping store used by {@code stored()} and {@code unique()}. Optional.
     *
     * @param mappingStore the store implementation to use
     * @return this builder
     */
    public Builder mappingStore(MappingStore mappingStore) {
      this.mappingStore = mappingStore;
      return this;
    }

    /**
     * Sets the null-handling policy. Defaults to {@link NullPolicy#PASS_THROUGH}.
     *
     * @param nullPolicy the null-handling policy to use
     * @return this builder
     */
    public Builder nullPolicy(NullPolicy nullPolicy) {
      this.nullPolicy = Objects.requireNonNull(nullPolicy, "nullPolicy");
      return this;
    }

    /**
     * Whether {@code stored()}/{@code unique()}/{@code context.mappings()} write the raw
     * canonical input as the store key, instead of the default purpose-separated
     * {@code HMAC(salt, input)} (section 5.1). An explicit, instance-wide opt-out of the
     * privacy-by-default behaviour, for debugging a store's contents directly. Defaults to
     * {@code false}.
     *
     * @param rawMappingKeys whether to write raw canonical input as the store key
     * @return this builder
     */
    public Builder rawMappingKeys(boolean rawMappingKeys) {
      this.rawMappingKeys = rawMappingKeys;
      return this;
    }

    /**
     * The retry budget {@code unique()} (section 5.3) exhausts before throwing
     * {@link AlterEgoCollisionException}. Defaults to 64; must be at least 1.
     *
     * @param uniqueMaxAttempts the retry budget; must be {@code >= 1}
     * @return this builder
     */
    public Builder uniqueMaxAttempts(int uniqueMaxAttempts) {
      this.uniqueMaxAttempts = uniqueMaxAttempts;
      return this;
    }

    /**
     * Validates the configuration and builds the {@link AlterEgo} instance.
     *
     * @return the configured {@link AlterEgo} instance
     */
    public AlterEgo build() {
      if (salt == null || salt.length < MIN_SALT_BYTES) {
        throw new AlterEgoConfigException(
            "salt is required and must be at least "
                + MIN_SALT_BYTES
                + " bytes, got: "
                + (salt == null ? "none" : salt.length + " bytes"));
      }
      if (uniqueMaxAttempts < 1) {
        throw new AlterEgoConfigException("uniqueMaxAttempts must be >= 1, got: " + uniqueMaxAttempts);
      }
      return new AlterEgo(salt, locale, mappingStore, nullPolicy, rawMappingKeys, uniqueMaxAttempts);
    }
  }
}
