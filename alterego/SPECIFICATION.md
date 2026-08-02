# AlterEgo — Specification

AlterEgo is a Java 25 library for deterministic pseudonymisation. It replaces personal or sensitive
values (names, addresses, dates, reference numbers) with realistic-looking substitutes, such that
the same input always produces the same output for a given configuration. It is designed for use in
Java streams:

```java
var pseudonymisedFirstNames = originalFirstNames.stream()
    .map(alterego.firstName())
    .toList();

var pseudonymisedBirthDates = originalBirthDates.stream()
    .map(alterego.shiftDate(30))
    .toList();
```

Everything observable about an output is defined by this specification plus Appendix A; nothing is
left to the implementer's choice of algorithm or the JDK's.

## 1. Goals and non-goals

### Goals

- **Deterministic**: the same input, salt, and configuration always produce the same output —
  across runs, across JVMs and JDK versions, and independent of the order in which inputs are
  processed. Determinism depends on nothing outside this library; the library never reads the
  system clock or any other ambient state.
- **Stream-friendly**: transformations are `Function<T, T>` values usable directly in `.map(...)`.
- **Configurable**: an `AlterEgo` instance carries locale, a secret salt, and a mapping store; all
  transformations obtained from it share that configuration.
- **Extensible**: clients can define their own strategies and get the same features as built-ins
  (determinism, uniqueness, stored mappings, record coherence).
- **Realistic output**: where a built-in draws from real vocabulary (first names, towns,
  organisation-name components), replacement values come from country-appropriate dictionaries;
  pattern-based output matches a caller-declared format.
- **Fictional by default**: where a recognised reserved-for-fiction or guaranteed-invalid value
  space exists (reserved email domains, Ofcom drama numbers, impossible postcodes), built-ins
  generate inside it; surnames and street names go further, drawing from authored, deliberately
  obvious vocabulary rather than real data at all — so pseudonymised data cannot accidentally
  reference something real (section 4.1).
- **Record-coherent**: fields of one record can be transformed consistently — town, postcode, and
  phone number agree — via an explicit record scope (section 6).
- **Zero runtime dependencies**: the library depends only on the JDK.

### Non-goals

- **Anonymisation.** Pseudonymisation is reversible by anyone holding the salt (or the mapping
  store). AlterEgo does not claim GDPR anonymisation; the documentation must say so plainly.
- **Free-text redaction / NER.** AlterEgo transforms structured values, not prose.
- **Cryptographic format-preserving encryption** (FF1/FF3). Outputs are not decryptable; reversal
  is only possible via a mapping store.
- **Format inference.** There is deliberately no generic "preserve whatever format the input has"
  transformation: consumers declare formats explicitly with `pattern(...)`. Built-ins that
  replace characters in place (email local parts, phone digits) document that behaviour as their
  own, per transformation.
- **Arbitrary value types.** Transformations operate on a fixed set of supported types
  (section 2.6). A public codec SPI is deliberately excluded from v1.

## 2. Core abstractions

### 2.1 Strategy

The unit of transformation logic. Stateless; all variability comes from the context.

```java
@FunctionalInterface
public interface Strategy<T> {
    T transform(T input, TransformationContext context);
}
```

### 2.2 TransformationContext

Created by the `Transformation` binding, fresh for every input value, and passed to
`Strategy.transform`. Contexts are single-use and must not be stored by a strategy.

```java
public interface TransformationContext {
    /** Deterministic randomness, derived from the salt, the domain, and this input value. */
    Randomness random();

    Locale locale();

    /** The namespace under which this transformation stores and looks up mappings. */
    String domain();

    /** Domain-scoped, key-hashing view of the mapping store (section 2.3). */
    Mappings mappings();

    /** Attributes shared across the fields of the current record, if any (section 6). */
    RecordAttributes record();

    /**
     * Child context for composite strategies (e.g. fullName delegating to firstName).
     * subInput is the canonical text form (section 2.6) of the sub-value.
     * Invariant: the returned context is derived exactly as a top-level transformation of
     * subInput under subDomain would be, so composite consistency (fullName agreeing with
     * firstName/lastName applied separately) holds by construction, not by convention.
     */
    TransformationContext derived(String subDomain, String subInput);
}
```

### 2.3 Mappings

Strategies never see the raw `MappingStore`. If they did, they would write raw plaintext keys and
silently defeat the hashed-key privacy default (section 5.1). The context instead exposes a view
that is scoped to the transformation's domain and hashes keys per Appendix A.4 before they reach
the store:

```java
public interface Mappings {
    Optional<String> get(String canonicalKey);
    String putIfAbsent(String canonicalKey, String value);
}
```

This is what client strategies use to maintain **persistent, cross-record** relationships between
values. For **ephemeral, intra-record** consistency, use record attributes instead (section 6).
The raw `MappingStore` SPI appears only on the builder; the `stored()`/`unique()` decorators use
it internally.

### 2.4 Randomness

Strategies do not receive a `java.util.random.RandomGenerator`. Exposing one would make a JDK
implementation part of AlterEgo's compatibility contract: any change to the chosen algorithm or
to `RandomGenerator`'s default methods across JDK releases would silently change every output.
Instead the context exposes a small library-owned interface whose behaviour is fully specified in
Appendix A:

```java
public interface Randomness {
    int nextInt(int bound);            // uniform in [0, bound); bound <= 0 throws
    long nextLong(long bound);         // uniform in [0, bound); bound <= 0 throws
    boolean nextBoolean();
    <T> T pick(List<T> choices);       // uniform choice; empty list throws
    char digit();                      // '0'..'9'
    char letterUpper();                // 'A'..'Z'
    char letterLower();                // 'a'..'z'
}
```

A `Randomness` is a stateful, single-threaded byte-stream consumer: each call consumes bytes from
an HMAC-SHA256 counter-mode stream over the derived key (Appendix A.2), so the sequence of calls a
strategy makes is part of its deterministic behaviour. `RandomGenerator` does not appear anywhere
in the public API.

### 2.5 Transformation

What `AlterEgo` hands out. It is a `Strategy` bound to an `AlterEgo` instance, exposed as a
`Function` so it drops straight into `.map(...)`, plus decorator methods:

```java
public interface Transformation<T> extends Function<T, T> {
    /** Guarantee distinct inputs map to distinct outputs (requires a MappingStore). */
    Transformation<T> unique();

    /** Persist input→output pairs in the MappingStore, and reuse them on later calls. */
    Transformation<T> stored();
}
```

Decorator algebra, so composition is never ambiguous: `unique()` subsumes `stored()`; both are
idempotent (`t.unique().unique()` ≡ `t.unique()`); `t.unique().stored()` ≡ `t.unique()`; and
`t.stored().unique()` ≡ `t.unique()`. Calling either without a configured `MappingStore` throws
`AlterEgoStoreException` immediately (not per element).

Transformations are immutable and thread-safe; one instance can be shared across threads and
reused across streams. Null handling is governed by the builder's `NullPolicy`:
`PASS_THROUGH` (default — `apply(null)` returns `null`) or `FAIL` (throws `AlterEgoException`).

### 2.6 AlterEgo and supported value types

The configured entry point. Immutable and thread-safe once built.

```java
AlterEgo alterego = AlterEgo.builder()
    .salt(secretBytes)                       // required; >= 16 bytes; a secret
    .locale(Locale.UK)                       // default: Locale.UK (a fixed constant)
    .mappingStore(new InMemoryMappingStore())// default: none (unique()/stored() then throw)
    .nullPolicy(NullPolicy.PASS_THROUGH)     // default
    .rawMappingKeys(false)                   // default: false (hashed keys — section 5.1)
    .uniqueMaxAttempts(64)                   // default: 64 (section 5.3)
    .build();
```

- **Salt**: required, minimum 16 bytes (shorter salts are trivially brute-forced; the builder
  rejects them). Accepted as `byte[]` or `char[]`; a `char[]` is converted via UTF-8. The builder
  copies the caller's array, so mutating the original afterwards has no effect; callers who passed
  a secret in a `byte[]`/`char[]` should still zero their own copy.
- **Lifecycle**: `AlterEgo` is `AutoCloseable`. `close()` (and its alias `destroy()`) zeroes the
  instance's salt bytes and marks the instance closed; any later factory call on it throws
  `IllegalStateException`. `close()` is idempotent. Closing is optional — an instance left open is
  not a leak beyond the salt residing in memory — but a try-with-resources block, or an explicit
  `destroy()`, lets security-sensitive callers bound how long the salt lives. Every
  `Transformation` shares the lifetime of the instance that produced it: because they share the
  one salt array that `close()` zeroes, a transformation's lifetime cannot outlive its parent's.
  Applying a transformation after its parent is closed therefore throws `IllegalStateException`
  (it does **not** silently derive from the zeroed salt). Close an instance only once every
  transformation built from it is done being used.
- **Locale**: defaults to the fixed constant `Locale.UK` (`en-GB`) — this library's primary
  deployment. A *fixed* default is deterministic on every machine; what remains banned is
  `Locale.getDefault()`, which would tie output to machine configuration (ADR 0006). Non-UK
  users configure explicitly; an unshipped country fails fast (section 4). v1 built-ins consult
  only the locale's **country**; the language component steers nothing yet.
- **`rawMappingKeys`**: applies to every `stored()`/`unique()`/`context.mappings()` use from this
  instance. `false` (default) writes purpose-separated `HMAC(salt, input)` store keys (section
  5.1, Appendix A.4); `true` writes the raw canonical input text as the key instead, for
  debugging a store's contents directly — an explicit, instance-wide opt-out of the
  privacy-by-default behaviour, not a per-transformation choice.
- **`uniqueMaxAttempts`**: the retry budget `unique()` (section 5.3) exhausts before throwing
  `AlterEgoCollisionException`, applied to every `unique()` transformation from this instance.
  Must be `>= 1`; the builder rejects anything less.

Built-in transformation factory methods (section 4) and binding methods for client strategies:

```java
Transformation<String> bind(String domain, Strategy<String> strategy);
<T> Transformation<T> bind(String domain, Class<T> type, Strategy<T> strategy);
```

`domain` namespaces both the derived randomness and the mapping store, so two different
transformations of the same input do not collide or correlate. Domains must match
`[A-Za-z0-9:._-]{1,100}` (validated at bind time) — this keeps the derivation message in
Appendix A.1 unambiguous. Built-ins use fixed, documented domain names (e.g.
`"alterego:first-name"`), which keeps outputs stable across library versions; clients should use
their own prefix (`"myapp:..."`).

Derivation and mapping-store persistence need a stable canonical text form for each value. Rather
than an open-ended codec SPI, AlterEgo supports a fixed set of types, chosen to mirror what
database columns typically store: text, numbers, dates, timestamps, flags, identifiers, and coded
values. Canonical forms **must be injective** (distinct values ⇒ distinct canonical text): a
non-injective form would give two distinct inputs the same pseudonym and silently break
`unique()`. The JDK `toString()` forms below are injective and are pinned as the canonical
encodings:

| Type                  | Canonical form                                                      |
|-----------------------|----------------------------------------------------------------------|
| `String`              | the value itself                                                    |
| `Integer`, `Long`     | `Integer.toString` / `Long.toString` (decimal, `-` sign)            |
| `Boolean`             | `toString()` — `true` / `false`                                     |
| `LocalDate`           | `toString()` — ISO-8601 (`2026-07-12`)                              |
| `LocalTime`           | `toString()` — ISO-8601                                             |
| `LocalDateTime`       | `toString()` — ISO-8601; note this omits zero seconds (`14:30`) and |
|                       | includes nanoseconds only when present; injective either way        |
| `Instant`             | `toString()` — ISO-8601 UTC                                         |
| `YearMonth`           | `toString()` — ISO-8601 (`2026-07`)                                 |
| `BigDecimal`          | `.stripTrailingZeros().toPlainString()`                             |
| `UUID`                | `toString()` — lower case                                           |
| any `enum`            | `name()`                                                            |

`bind(domain, type, strategy)` throws `AlterEgoConfigException` immediately for an unsupported
`type` (fail fast, not per element). Enums are recognised via `Class::isEnum`.

## 3. Determinism model

This is the heart of the library and the part that is easiest to get wrong. The normative
byte-level definition lives in Appendix A; this section states the properties.

### 3.1 Per-input derivation

The randomness available to a strategy is **derived from the input value**, not shared across a
stream. Conceptually:

```
key = HMAC-SHA256(salt, purpose || domain || canonical(input) || counter)   // Appendix A.1
```

The context's `Randomness` is an HMAC-SHA256 counter-mode stream over `key` (Appendix A.2).
Consequences, all required behaviour:

- The same input gives the same output regardless of position in the stream.
- Parallel streams are safe: no shared mutable generator state.
- Reordering, filtering, or deduplicating the input data does not change any individual mapping.
- Two datasets processed with the same salt produce consistent mappings, so referential integrity
  (e.g. a name appearing in two tables) is preserved without any shared state.

The `purpose` tag separates uses of the same salt/domain/input triple: randomness keys,
mapping-store keys, and record-attribute keys are derived with different purposes (Appendix A.1),
so a party who can read the mapping store cannot reconstruct randomness keys and regenerate
outputs. The `counter` is `0` except when the `unique()` decorator re-derives to escape a
collision (section 5.3).

### 3.2 The salt is a secret

Because the mapping from input to output is `HMAC(salt, input)`-driven, anyone with the salt can
confirm guesses ("does 'Alice' map to this output?"). The salt must be treated like a key:
supplied as `byte[]` or `char[]`, never logged, and documented as secret.

### 3.3 Inherent privacy limits

Two limits follow from determinism itself and are documented rather than hidden:

- **Frequency is preserved.** Deterministic pseudonymisation maps equal inputs to equal outputs,
  so the most common surname in the input is the most common pseudonym in the output. An
  attacker with population statistics can make good guesses about frequent values without the
  salt; similarly, a jittered date remains close to the truth by construction (`shiftDate(30)`
  keeps it within 30 days). Where this matters, the mitigation is aggregation or suppression —
  out of scope (section 1).
- **Low-cardinality values gain almost nothing.** A deterministic mapping of a `Boolean` or a
  small enum is a relabelling of a handful of values and provides essentially no protection on
  its own. These types are supported chiefly so custom and composite strategies can cover
  whole records; pseudonymising such a column in isolation should not be mistaken for
  protecting it.

### 3.4 Output stability

Outputs are a function of: salt, locale, domain, dictionary contents, and the Appendix A
algorithms — all of which are owned by this library, none by the JDK. Therefore:

- Dictionaries are versioned; a dictionary change is a breaking change and is called out in
  release notes.
- The Appendix A algorithms and canonical encodings are frozen within a major version, enforced
  by committed test vectors (section 10).
- Tests pin exact expected outputs for a reference salt to detect accidental drift.
- **The library never reads the clock.** Constraints like "never a future date" are expressed as
  explicit, caller-supplied bounds (section 4.5); if a bound should mean "today", the caller
  computes it and visibly owns the run-dependence that implies.

## 4. Built-in transformations

All are locale-aware where meaningful, and honour the `NullPolicy`. Factory methods fail fast
with `AlterEgoConfigException` (throw at call time, not per element) for configuration problems:
no resources for the locale's country, malformed pattern, invalid options.

Realistic replacement values are a property of the **country**, not the language. A dataset in
Welsh is not a dataset about Wales, just as `en-GB` implies the English language, not an England
location: names, towns, streets, postcodes, and phone numbers in data are UK-wide regardless the
language of the application. Everything the built-ins consult — dictionaries (names, towns,
streets, organisation components) and structural rules (postcode formats, fictional phone
ranges, recognised legal suffixes) — therefore resolves by the locale's **country**: exact
country match, else `AlterEgoConfigException` (never a silent borrow from another country). The
dictionaries are UK-wide pools that naturally include Welsh, Scottish, and Northern Irish
names and places; although currently town and street entries use their English-language forms
(Swansea, not Abertawe).

Consequences: `cy-GB` and `en-GB` are configuration synonyms for the v1 built-ins (an
equivalence test enforces this, section 10); a locale without a country (e.g. `Locale.of("en")`)
fails fast for country-scoped transformations; the locale's language component steers nothing in
v1 and is reserved for future language-sensitive generation. v1 ships country `GB`; others,
starting with `US`, are post-v1.

### 4.1 Fictional by default

Some value spaces have officially reserved or structurally impossible regions: values that look
right but are guaranteed never to identify a real person, deliver mail, connect a call, or route
a message. Where such a region exists, the built-in generates inside it **by default**:

| Transformation               | Guarantee                    | Mechanism                             |
|------------------------------|------------------------------|---------------------------------------|
| `emailAddress()`             | never a working mailbox      | RFC 2606 reserved domains             |
|                              |                              | (`example.com`, `.org`, `.net`)       |
| `domainName()`               | never a routable domain      | RFC 2606 reserved domains and TLDs    |
|                              |                              | (`.test`, `.example`, `.invalid`)     |
| `url()`                      | never a working link         | URL with a RFC 2606 reserved domain   |
| `phoneNumber()`              | never a connectable number   | Ofcom drama ranges (e.g.              |
|                              |                              | `020 7946 0xxx`, `07700 900xxx`,      |
|                              |                              | `01632 960xxx`); optionally includes  |
|                              |                              | non-geographic drama ranges           |
| `postcode()`                 | never a deliverable postcode | plausible outward code, but the       |
|                              |                              | inward code ends in a letter never    |
|                              |                              | used in real postcodes (`C I K M O V`)|
| `lastName()`                 | reads as obviously fictional,| authored (not sourced) surname        |
|                              | not a real person's surname  | vocabulary (e.g. "Testperson")        |
| `streetAddress()`            | reads as obviously fictional,| authored (not sourced) theme word     |
|                              | not a real street            | (e.g. "Example") plus a real          |
|                              |                              | structural type word ("Road")         |
| `nhsNumber()`                | never a live NHS number      | the NHS test range: numbers beginning |
|                              |                              | `999` are reserved for test data and  |
|                              |                              | never issued; check digit valid (A.5) |
| `nationalInsuranceNumber()`  | never an allocated NI number | prefix `QQ` — `Q` is never used as a  |
|                              |                              | first letter in allocated prefixes;   |
|                              |                              | HMRC's own documentation example      |
| `drivingLicenceNumber()`     | never a real GB driving      | surname block `99999` — impossible on |
|                              | licence number               | a real licence, where a real surname  |
|                              |                              | always contributes at least one letter|
| `passportNumber()`           | never a valid UK passport    | two leading letters (`ZZ`) — real UK  |
|                              | number                       | passport numbers are wholly numeric   |
| `creditCardNumber()`         | never an issued card number  | leading digit `0`, an ISO/IEC 7812    |
|                              |                              | major industry identifier no card     |
|                              |                              | scheme issues from; Luhn check digit  |
|                              |                              | valid (A.9)                           |

Guarantees key on the locale's **country** (section 4 intro): any UK locale (ISO code `GB`),
whatever its language, gets the UK mechanisms.

Two things follow from the mechanism and are documented plainly:

- **Fictional values pass format checks but fail live lookups.** Drama ranges are format-valid
  (that is why Ofcom reserved them) and reserved domains are syntax-valid, so regex-shaped
  validation accepts the output; a system that validates against live reference data (PAF, number
  allocation, MX lookup) will reject it — usually exactly the point of pseudonymised data.
- **Opting out is explicit.** Where full realism matters more than the guarantee, options such as
  `PhoneOptions.realistic()` or `PostcodeOptions.realistic()` disable it, and their documentation
  states the risk: realistic output can collide with a real person's number or address.

`lastName()` and `streetAddress()` use a different mechanism from the other three: there is no
officially reserved "fictional surname" or "fictional street" space to draw from, so their entire
vocabulary is authored rather than sourced from real UK population/geographic data, reviewed to
avoid coinciding with a known real name (ADR 0010). This is a curation-time guarantee, not a
structural or regulatory one like the other three: it depends on the wordlist actually having been
reviewed carefully, not on an external authority's own reserved-value-space rules. The policy is
per-category, not per-locale — any future country's surname/street dictionaries are authored the
same way, so the guarantee doesn't require a separate "fictional" locale per real one.

The identifier transformations (section 4.8) fall into the same two families: `nhsNumber()`
draws from an officially reserved test range, while `nationalInsuranceNumber()`,
`drivingLicenceNumber()`, `passportNumber()`, and `creditCardNumber()` rely on structural
impossibility — a prefix or field value a real identifier can never carry, chosen so the output
still passes shape-and-checksum validation.

Countries with no defined fictional range fall back to in-place digit replacement with **no
guarantee**; the Javadoc of each built-in states, per country, which category applies. No
guarantee of this kind is possible for first names, towns, or organisation names — each output
word is real (that is what makes it realistic); only the combination and its attachment to a
record are synthetic. Candidate future additions in the same spirit: TEST-NET IP addresses
(RFC 5737) and `.test`/`.invalid` domains (RFC 6761).

### 4.2 People and organisations

| Method               | Behaviour                                                       |
|----------------------|-----------------------------------------------------------------|
| `firstName()`        | Replacement drawn from the country's first-name dictionary.     |
| `lastName()`         | Replacement drawn from the country's surname dictionary —       |
|                      | authored to read as obviously fictional (section 4.1), not real |
|                      | population data.                                                 |
| `fullName()`         | Tokenises and delegates to the name strategies (see below).     |
| `organisationName()` | Generated from country-appropriate component lists, preserving  |
|                      | a recognised legal suffix if present. Recognised suffixes are a |
|                      | per-country resource; UK includes both the English forms        |
|                      | ("Ltd", "plc") and the Welsh company forms ("Cyf.", "c.c.c.").  |

The Welsh suffixes are the abbreviated forms Companies Act 2006 ss.58(2) and 59(2) permit as
alternatives to "plc"/"public limited company" and "Ltd"/"Limited" respectively for a Welsh
company; the Act also permits the corresponding full Welsh words ("cwmni cyfyngedig cyhoeddus",
"cyfyngedig"), not shipped as separate dictionary entries in v1.

Dictionary entries are emitted as stored (title case); v1 does not mirror the input's casing.

`fullName()` tokenisation, pinned so outputs are reproducible:

1. Trim; split on whitespace runs; output tokens are joined with single spaces.
2. Blank input is returned unchanged. One token is transformed under the surname domain.
3. Two or more tokens: first token under the first-name domain, last token under the surname
   domain, middle tokens under the first-name domain.
4. A hyphenated token is split on `-`, each segment transformed under the token's domain, and
   rejoined with `-` (so `Smith-Jones` yields a hyphenated surname).
5. Titles and suffixes ("Dr", "Jr") get no special handling in v1; they are transformed as
   ordinary tokens. Documented limitation.

Each part goes through `context.derived(...)` with the corresponding built-in domain, so
`fullName()` output agrees with `firstName()`/`lastName()` applied to the parts separately.

Options (per-transformation, e.g. `firstName(NameOptions.preserveInitial())`):

- `preserveInitial()` — output starts with the same letter as the input. If the dictionary has no
  entry with that initial, the option is ignored for that input (unconstrained pick,
  deterministic).
- Name dictionaries are flat, untagged lists in v1; tagged name dictionaries (e.g. by gender)
  are a possible later extension, not attempted in v1 because inference from the input is
  unreliable. (Town dictionaries do carry structural tags — section 6.3.)

### 4.3 Addresses

| Method            | Behaviour                                                            |
|-------------------|----------------------------------------------------------------------|
| `streetAddress()` | House number drawn deterministically from 1–299, plus a complete     |
|                   | street name composed from the country's dictionary (an authored,     |
|                   | obviously-fictional theme word plus a real structural type word,     |
|                   | e.g. "Example Road" — section 4.1) — the dictionary, not the code,   |
|                   | owns the vocabulary.                                                  |
| `city()`          | Replacement from the country's town/city dictionary.                 |
| `postcode()`      | Country-specific format with the fictionality guarantee of 4.1 where |
|                   | the country defines one (UK: impossible inward-code letters).        |

Inside a record scope, `city()`, `postcode()`, and `phoneNumber()` cohere via record attributes
(section 6.3): the first of them to run ties down the record's place, and the others follow it.

### 4.4 Contact details

| Method           | Behaviour                                                                  |
|------------------|----------------------------------------------------------------------------|
| `emailAddress()` | In the local part, each ASCII letter/digit is replaced class-wise (case    |
|                  | preserved) and other characters are kept; the domain is drawn from the     |
|                  | RFC 2606 reserved set by default (guaranteed non-working, section 4.1), or |
|                  | preserved/mapped via options.                                              |
| `phoneNumber()`  | Digits replaced, punctuation and grouping kept; output lands in the        |
|                  | country's reserved fictional range by default (section 4.1);               |
|                  | `PhoneOptions.realistic()` opts out.                                       |

`emailAddress()` splits at the **last** `@`; input containing no `@` is treated as a bare local
part and the output gains `@` plus the chosen reserved domain.

### 4.5 Temporal jitter

```java
alterego.shiftDate(30)                              // Transformation<LocalDate>, ±30 days
alterego.shiftDateTime(30, AlterEgo.TimeField.HOUR)  // Transformation<LocalDateTime>
alterego.shiftInstant(30, 86400)                    // Transformation<Instant>, ±30 days, ±1 day seconds
```

Eight factory methods, each pairing a date strategy with (for `LocalDateTime`) a time strategy;
`AlterEgo.DateField` and `AlterEgo.TimeField` are nested enums selecting which strategy runs. Each
of the eight also has a twin taking a trailing `JitterOptions<T>` for clamping — sixteen methods
in total.

**Date strategies** — `LocalDate`, and the date part of every `shiftDateTime(...)` call:

| Call                                  | Behaviour                                              |
|---------------------------------------|---------------------------------------------------------|
| `shiftDate(int days)`                 | Whole-day shift, uniform over `[-days, +days]`         |
|                                        | (Appendix A.3).                                         |
| `shiftDate(AlterEgo.DateField field)` | `MONTH`: uniform random day within the input's own     |
|                                        | year and month. `YEAR`: uniform random day within the  |
|                                        | input's own year, leap-aware. Drawn via                |
|                                        | `nextInt(lengthOfMonth)` / `nextInt(lengthOfYear)`     |
|                                        | (Appendix A.3), 1-based.                                |

**Time strategies** — the time part of `shiftDateTime(...)` only, as the trailing argument(s):

| Call                               | Behaviour                                                 |
|-------------------------------------|------------------------------------------------------------|
| `int seconds`                      | Whole-second shift, uniform over `[-seconds, +seconds]`.  |
| `LocalTime start, LocalTime end`   | Uniform random point in `[start, end]` inclusive, to the  |
|                                     | second (`nextInt(endSecondOfDay - startSecondOfDay + 1)`);|
|                                     | `start` after `end` is an `AlterEgoConfigException` at    |
|                                     | call time.                                                 |
| `AlterEgo.TimeField.HOUR`          | Same hour as the input; uniform random minute, then       |
|                                     | second, each `nextInt(60)`.                                |

giving the six overloads `shiftDateTime(int days, int seconds)`,
`shiftDateTime(int days, LocalTime start, LocalTime end)`,
`shiftDateTime(int days, AlterEgo.TimeField field)`,
`shiftDateTime(AlterEgo.DateField field, int seconds)`,
`shiftDateTime(AlterEgo.DateField field, LocalTime start, LocalTime end)`, and
`shiftDateTime(AlterEgo.DateField field, AlterEgo.TimeField field)`. The date component is always
drawn before the time component.

**Instant strategies** — for `shiftInstant(int days, int seconds)`:
Independent whole-day shift and whole-second shift, bounded by `days` and `seconds` respectively, applied to an `Instant`. Sub-second precision is preserved, not zeroed.

- Nanoseconds are zeroed in the output of every `shiftDateTime(...)` overload, unconditionally,
  regardless of which time strategy ran — carrying them over unperturbed from the input would leak
  sub-second precision that is itself close to unique per record.
- `JitterOptions<T>` (`T` is `LocalDate`, `LocalDateTime`, or `Instant`, matching the method) clamps the
  result, applied last, after the strategy has run:
  - `JitterOptions.min(value)`, `JitterOptions.max(value)`, `JitterOptions.minmax(min, max)` — an
    **inclusive** bound, or both in one call. Values that would fall outside a bound are clamped
    to it, not rejected — values near a bound pile up on it; documented, not hidden. The library
    never reads the clock (section 3.4): a caller wanting "no future dates" writes
    `JitterOptions.max(LocalDate.now())`. Note the type-dependent meaning of "past", which the
    caller owns: a `LocalDate` strictly in the past excludes today
    (`max(LocalDate.now().minusDays(1))`), whereas a `LocalDateTime` strictly in the past is
    anything before now (`max(LocalDateTime.now().minusNanos(1))`).
  - `JitterOptions` is an immutable value type with no "no bounds" state of its own — an unclamped
    call simply omits the trailing `JitterOptions` argument.
- Because every draw is derived from the input value, equal timestamps jitter identically —
  preserving equality relationships in the data. Ordering is **not** preserved: two distinct
  inputs' shifts are drawn independently, so a date before another in the input can land after it
  in the output (most likely when their true difference is small relative to the jitter range).

### 4.6 Pattern-based strategies

```java
alterego.pattern("DLDDDL")     // e.g. "3K481Z"
alterego.pattern("LLDD DLL")   // e.g. "GU12 4XY"
```

Pattern language (v1, deliberately small):

| Token | Meaning                                       |
|-------|-----------------------------------------------|
| `D`   | random digit `0-9`                            |
| `L`   | random uppercase letter `A-Z`                 |
| `l`   | random lowercase letter `a-z`                 |
| `A`   | random letter, either case (Appendix A.3)     |
| `\x`  | literal `x` (escapes the above, and `\\`)     |
| other | literal, copied to the output                 |

Patterns are compiled once (at `pattern(...)` call time); a malformed pattern (e.g. trailing `\`)
throws `AlterEgoPatternException` with the offending position. A raw pattern carries no
fictionality guarantee (section 4.1): `pattern("LLDD DLL")` can and will produce real postcodes —
use `postcode()` when the guarantee matters. Possible later extensions, explicitly out of scope
for the pattern language itself: character classes `[ABC]` and repetition counts `D{5}`.
Checksum-carrying identifiers (NHS numbers, card numbers) are deliberately built-ins with pinned
fictional value spaces (section 4.8), not generic pattern tokens — a generic Luhn token could not
also express the never-issued-prefix rules that make the output safe.

There is deliberately no generic format-inferring transformation (section 1 non-goals):
consumers state the format they want. Built-ins that replace characters in place (email local
parts, phone digits) do so as documented per-transformation behaviour, not via a public
inference facility.

### 4.7 Utility

| Method                        | Behaviour                                                 |
|-------------------------------|------------------------------------------------------------|
| `constant(T value)`           | Replace everything with a fixed value.                     |
| `redact(Class<T> type)`       | Replace everything with a fixed type-appropriate default   |
|                               | (see below), for schema-preserving redaction without       |
|                               | naming the constant.                                        |
| `mask(char c)`                | Mask every character with `c`; equivalent to               |
|                               | `mask(c, 0)`.                                               |
| `mask(char c, int keepLast)`  | Mask all but the last `keepLast` characters with `c`.      |
|                               | Inputs of length ≤ `keepLast` are returned unchanged;      |
|                               | negative `keepLast` throws `AlterEgoConfigException`.      |

`redact(type)` returns `constant(default)` for the given type, so it inherits every property of
`constant` (deterministic, order-independent, ignores the input). The defaults are the natural
zero for each supported value type: `""` (`String`), `0` (`Integer`), `0L` (`Long`), `false`
(`Boolean`), `1970-01-01` (`LocalDate`), `1970-01-01T00:00` (`LocalDateTime`), the epoch
(`Instant`), `00:00` (`LocalTime`), `1970-01` (`YearMonth`), `0` (`BigDecimal`), and the
all-zeroes UUID. A type with no obvious safe default — notably any `enum` — throws
`AlterEgoConfigException`; use `constant(value)` with an explicit value for those.

### 4.8 Identifier transformations (UK documents and payment cards)

Five `Transformation<String>` built-ins for common identifying numbers. Each generates a
complete replacement in a pinned output format — the input's content is only the derivation
input (section 3.1) and is otherwise ignored; there is no in-place digit preservation, no
options type, and no blank-input special case (the empty string is an input like any other).
Every output satisfies the identifier's shape (and checksum, where one exists) while landing in
the fictional space of section 4.1. The generation algorithms, including the exact order of
randomness draws, are pinned in Appendix A.5-A.9.

| Method                        | Output format (fixed)               | Domain                                |
|-------------------------------|--------------------------------------|----------------------------------------|
| `nhsNumber()`                 | `999 ddd dddc` — 10 digits, 3-3-4   | `alterego:nhs-number`                 |
|                               | spacing, `c` = valid mod-11 check   |                                        |
|                               | digit (A.5)                          |                                        |
| `nationalInsuranceNumber()`   | `QQ dd dd dd S` — `S` drawn from    | `alterego:national-insurance-number`  |
|                               | `A`-`D` (A.6)                        |                                        |
| `drivingLicenceNumber()`      | 16 characters, unspaced, DVLA       | `alterego:driving-licence-number`     |
|                               | (Great Britain) layout: `99999` +   |                                        |
|                               | 6-digit encoded date-of-birth block |                                        |
|                               | + 2 initial letters + `9` + 2       |                                        |
|                               | letters (A.7)                        |                                        |
| `passportNumber()`            | `ZZddddddd` — `ZZ` plus 7 digits,   | `alterego:passport-number`            |
|                               | unspaced (A.8)                       |                                        |
| `creditCardNumber()`          | `0ddd dddd dddd dddc` — 16 digits   | `alterego:credit-card-number`         |
|                               | in four spaced groups, `c` = valid  |                                        |
|                               | Luhn check digit (A.9)               |                                        |

Country scoping: `nhsNumber()`, `nationalInsuranceNumber()`, `drivingLicenceNumber()`, and
`passportNumber()` are UK document formats and require the locale's country to be `GB`; any
other country throws `AlterEgoConfigException` at factory-call time (the same fail-fast rule as
the dictionary-backed built-ins). `creditCardNumber()` is locale-independent (like
`emailAddress()`): ISO/IEC 7812 and Luhn are not country-specific.

Fictionality mechanisms, and what each rests on:

- **`nhsNumber()`** — NHS numbers beginning `999` are reserved for test and synthetic data and
  are never issued to a person (NHS England test-data guidance). This is a documented reserved
  range, the same guarantee family as Ofcom drama phone numbers. The check digit is computed
  normally (A.5), so the output passes full NHS-number validation including the checksum.
- **`nationalInsuranceNumber()`** — HMRC's published prefix-validity rules never allocate
  prefixes whose first letter is `D`, `F`, `I`, `Q`, `U`, or `V`; `QQ` in particular is the
  prefix HMRC itself uses for example NI numbers in documentation. A `QQ`-prefixed number is
  structurally unallocatable. NI numbers carry no checksum; the suffix letter is drawn from the
  valid set `A`-`D`.
- **`drivingLicenceNumber()`** — in the DVLA format, characters 1-5 encode the licence holder's
  surname, padded with `9`s only after the surname's own letters. A real surname always
  contributes at least one letter, so the block `99999` can never occur on a real licence. The
  remaining fields (date-of-birth block, initials, issue letters) are generated shape-valid
  (A.7) so the output passes DVLA-format validation. Northern Ireland's separate DVA format
  (8 digits) is not generated in this version — the output is always the Great Britain layout.
- **`passportNumber()`** — UK passport numbers are wholly numeric (9 digits); an output carrying
  the letters `ZZ` can never be a valid UK passport number, while still passing the generic
  passport-field validation (up to 9 alphanumeric characters) used by systems that accept
  multiple nationalities' documents. The guarantee is scoped to UK passports, like the postcode
  guarantee is scoped to UK postcodes.
- **`creditCardNumber()`** — ISO/IEC 7812 reserves major industry identifier `0` (the first
  digit) for ISO/TC 68 and future industry assignment; no payment card scheme issues PANs
  beginning `0`. The Luhn check digit is computed normally (A.9), so the output passes
  length-and-checksum validation while being unmistakably outside every issuing range.

As with section 4.1's other structural guarantees, these outputs pass shape/checksum validation
but fail live lookups (a PDS trace, a DVLA record check, an issuer BIN lookup) — usually exactly
the point. There are deliberately no `realistic()` opt-outs for these five: a "realistic" NHS,
NI, licence, passport, or card number is a real person's credential-shaped identifier, and the
risk-to-value ratio is far worse than for phone numbers or postcodes. Systems that need
network-testable card numbers should use the card networks' own published test PANs, not this
library.

## 5. Uniqueness and stored mappings

### 5.1 MappingStore SPI

```java
public interface MappingStore {
    Optional<String> get(String namespace, String key);

    /** Atomic; returns the value that ended up stored (the existing one on a race). */
    String putIfAbsent(String namespace, String key, String value);

    /**
     * Store key→value only if the key has no mapping AND the value is unused as an output
     * in this namespace. Must be atomic as a whole (one transaction for a JDBC store).
     */
    PutUniqueResult putIfAbsentUnique(String namespace, String key, String value);

    sealed interface PutUniqueResult {
        record Stored() implements PutUniqueResult {}
        record ExistingMapping(String value) implements PutUniqueResult {}
        record ValueTaken() implements PutUniqueResult {}
    }
}
```

- Namespaces are transformation domains, so different transformations never interfere. Using the
  same domain with different decorator stacks (e.g. once with `unique()`, once without) is a
  client error and is documented as such.
- Uniqueness is expressed as a single atomic operation, not a separate reserve-then-put dance:
  a two-step protocol cannot be made leak-free (a crash or lost race between the steps strands a
  reserved value), and a single operation is straightforward to implement with one transaction or
  one lock.
- Implementations must be thread-safe (parallel streams).
- The library ships `InMemoryMappingStore` (`ConcurrentHashMap`-based, with an inverse index per
  namespace to make the value-in-use check O(1)). Its memory use grows without bound with the
  number of distinct inputs — documented; large or long-lived datasets belong in a persistent
  store. The library also ships `FileMappingStore` (section 5.4), a single-process persistent
  store backed by one local file. JDBC- or Redis-backed stores are left to clients or future
  modules; the SPI plus the contract test (section 10) are the contract.
- **Privacy**: by default the *key* written to the store is the purpose-separated
  `HMAC(salt, input)` from Appendix A.4, encoded as 64 lowercase hex characters — the store never
  contains raw input data, and store contents cannot be used to reconstruct randomness keys.
  Storing raw keys is opt-in for debugging (`AlterEgo.Builder.rawMappingKeys(true)`, section 2.6).
  Keys are never decoded; stored *values* are decoded
  via the canonical forms of section 2.6, and a value that fails to decode (corrupted store,
  renamed enum constant) throws `AlterEgoStoreException`.

### 5.2 `stored()`

Decorator that persists mappings: on each call, look up the input's key; if present, return the
stored output; otherwise run the underlying strategy and `putIfAbsent` the result. Use when
mappings must survive dictionary/algorithm upgrades, or must be shared with an external process.

### 5.3 `unique()`

Decorator guaranteeing distinct inputs never map to the same output. Subsumes `stored()`. For
each input:

1. `get(key)`; if a mapping exists, return it.
2. Run the underlying strategy to get a candidate (retry counter `0`).
3. `putIfAbsentUnique(key, candidate)`:
   - `Stored` — return the candidate.
   - `ExistingMapping(v)` — another thread mapped this same input concurrently; return `v`.
   - `ValueTaken` — increment the retry counter, re-derive the context (Appendix A.1), generate a
     new candidate, and repeat step 3.
4. After `AlterEgo.Builder.uniqueMaxAttempts` attempts (default 64, section 2.6), throw
   `AlterEgoCollisionException` — with a message pointing out the likely cause (output space too
   small for the input volume).

Uniqueness necessarily depends on the mapping store's lifetime: it holds across everything that
shares one store, and no further. This is documented rather than hidden.

**Order-independence caveat.** Undecorated transformations are fully order-independent
(section 3.1). `unique()` is the one necessary exception: when two inputs' natural candidates
collide, whichever input is processed *first* keeps the natural candidate and the other is
re-derived. Absent collisions — the overwhelmingly common case — `unique()` outputs are identical
regardless of processing order; on collision, only the colliding inputs are affected, and the
resolution is captured in the mapping store so it remains stable on every later run. This is
inherent to any uniqueness guarantee, and the documentation says so.

### 5.4 File-backed store: FileMappingStore

`FileMappingStore` is the shipped persistent `MappingStore`: one local file, one process,
JDK-only (`java.nio`). It is what makes the cross-run stability promises of 5.2/5.3 achievable
without writing a custom store (ADR 0011).

```java
public final class FileMappingStore implements MappingStore, AutoCloseable {
    public static FileMappingStore open(Path file);  // creates the file if absent
    @Override public void close();
}
```

**Lifecycle.**

- `open(file)` creates the file if it does not exist (the parent directory must already exist),
  acquires an **exclusive file lock** (`FileChannel.tryLock`) held for the store's lifetime, and
  replays the file into an in-memory index (same structure as `InMemoryMappingStore`: forward
  map plus per-namespace inverse index). If the lock is already held — another store instance in
  this or any other process has the file open — `open` throws `AlterEgoStoreException`. The
  store is single-process by design; multi-process sharing needs an external store.
- All operations after `close()` throw `AlterEgoStoreException`. `close()` is idempotent and
  releases the lock. The store is thread-safe within its process (writes serialised on an
  internal monitor; reads served from the in-memory index).
- Reads (`get`) never touch the file after `open`. Writes append exactly one record per **newly
  stored mapping** — `putIfAbsent` on an existing key and `putIfAbsentUnique` returning
  `ExistingMapping`/`ValueTaken` write nothing. The append is flushed to the OS before the call
  returns success. There is no fsync: an OS-level crash can lose the final record(s), which the
  torn-tail rule below makes safe; a process crash cannot, because success is only reported
  after the write.
- The file only grows, by one line per distinct stored mapping — the same asymptotic footprint
  as the in-memory store, and no compaction is needed because records are never superseded
  (mappings are permanent by contract).

**File format (v1, frozen like the A.4 key encoding — this file is persistent user data).**

- UTF-8 text, `\n` line terminators only. A record is complete iff its line ends in `\n`.
- Line 1 (header): exactly `alterego-mapping-store 1`. On creating a new (or empty) file the
  store writes this header; on opening a non-empty file whose first complete line is anything
  else, `open` throws `AlterEgoStoreException`.
- Every subsequent line is one mapping:
  `namespace + "\t" + base64url(key) + "\t" + base64url(value)`, where `base64url` is
  `java.util.Base64.getUrlEncoder().withoutPadding()` over the string's UTF-8 bytes. The
  namespace is written verbatim (domains match `[A-Za-z0-9:._-]{1,100}`, section 2.6, so it can
  never contain a tab or newline); key and value are always encoded, because raw mapping keys
  (`rawMappingKeys(true)`) may contain anything.
- **Torn-tail rule**: a final line with no trailing `\n` is an interrupted append. It is ignored
  on replay, and the next append overwrites it (the store positions writes at the end of the
  last complete line). This is safe because a torn record's `putIfAbsent`/`putIfAbsentUnique`
  call never returned success.
- Any other malformation — a non-final line that does not parse, a wrong field count, an invalid
  base64 field, or a **duplicate (namespace, key)** — is corruption or external editing, and
  `open` throws `AlterEgoStoreException` naming the file and line number. The store never
  silently drops or repairs interior data.

Transforming the fields of a record independently can produce incoherent combinations: a record
reading *Manchester, E4 0VV, 020 4966 3211* mixes a northern town, a London postcode, and a
London phone number. Similarly a Companies House number prefix implies a UK country (`SC...` is
Scotland, `NI...` is Northern Ireland). Record coherence lets related fields agree.

### 6.1 RecordScope

A `RecordScope` bounds one record's transformation. It is created per record, used from a single
thread, and closed when the record is done (its attributes are then discarded). A single thread is
not an arbitrary restriction: first-touch-wins (section 6.2) only has one deterministic winner if
"first" is well-defined, and it is not across threads, which race. A parallel *stream of records*
is fine — each element gets its own scope, and each scope still sees only one thread — but never
share one `RecordScope` instance across threads:

```java
try (RecordScope rec = alterego.record()) {          // or: alterego.record("case-12345")
    out.town     = rec.apply(alterego.city(),        in.town);
    out.postcode = rec.apply(alterego.postcode(),    in.postcode);
    out.phone    = rec.apply(alterego.phoneNumber(), in.phone);
}
```

```java
public interface RecordScope extends AutoCloseable {
    /** Apply a transformation with this record's attributes visible to its strategy. */
    <T> T apply(Transformation<T> transformation, T value);

    /** Pre-seed an attribute (e.g. a known region) before any field is transformed. */
    <A> RecordScope with(AttributeKey<A> key, A value);

    @Override void close();
}
```

Scopes are cheap; in a stream of records, create one per element (safe under parallel streams —
each element has its own scope). Transformations applied *outside* any scope behave exactly as
before: every field independent.

### 6.2 Record attributes

Shared state within a scope is a set of typed attributes, reached from any strategy via
`context.record()`:

```java
public final class AttributeKey<A> {
    public static <A> AttributeKey<A> of(String name, Class<A> type) { ... }
    // name obeys the domain charset of section 2.6
}

public interface RecordAttributes {
    <A> Optional<A> get(AttributeKey<A> key);

    /** Resolve-once: the first caller's resolver fixes the value for the whole record. */
    <A> A computeIfAbsent(AttributeKey<A> key, Function<Randomness, A> resolver);

    /** First write wins; a second set with an equal value is a no-op; a conflicting
        value throws AlterEgoCoherenceException — incoherence should be loud. */
    <A> void set(AttributeKey<A> key, A value);

    /** True inside a real scope, false outside any scope — costs no randomness and touches no
        attribute, unlike the other three methods; lets a strategy check cheaply whether
        attempting to *establish* shared state (not just read one) is worth doing at all. */
    boolean isActive();
}
```

Semantics, chosen so strategy code is identical in and out of a scope:

- **First touch wins.** Whichever strategy first needs or sets an attribute fixes it; later
  strategies see that value. Asking for the town first ties down the postcode; asking for the
  postcode first ties down the town.
- **Determinism.** Inside a scope, an output may additionally depend on the record's attributes.
  Process a record's fields in a stable order (application code naturally does) and the whole
  record is reproducible. With a **keyed** scope (`alterego.record(key)`), `computeIfAbsent`
  resolvers receive `Randomness` derived from the record key and the attribute name
  (Appendix A.1, purpose `alterego/1/record`) — so attribute values resolved that way are
  independent even of field order. In an anonymous scope the resolver receives the asking
  strategy's own `Randomness` (first-asker resolution, documented).
- **Outside any scope**, `get` is empty, `set` is discarded, and `computeIfAbsent` runs its
  resolver and returns the value without retaining it — fields stay independent and strategies
  need no scope-awareness branching.
- **Composites.** A `derived(...)` context (section 2.2) shares its parent's record attributes:
  the section 2.2 invariant concerns key derivation only, not record state, so `fullName()`-style
  delegation coheres inside a scope like any other strategy.
- Strategies that do not care about record state (e.g. a six-digit reference number) simply never
  touch `record()` and are entirely unaffected.

Record attributes are **not** the `MappingStore` and deliberately do not use its SPI: the store
is persistent, cross-record, and pluggable; attributes are ephemeral, intra-record, in-memory
state with the scope's lifetime — nothing about them needs to be pluggable (ADR 0008).

### 6.3 Built-in coherence

The built-ins share two published attribute keys (constants on `AlterEgoAttributes`):

- `UK_POSTCODE_AREA` (`String`, e.g. `"M"` for Manchester) — set by `city()` from the chosen
  town's dictionary tag, or resolved by `postcode()` if it runs first.
- `UK_NATION` (`UkNation` enum: `ENGLAND`, `WALES`, `SCOTLAND`, `NORTHERN_IRELAND`) — implied
  by the postcode area.

Behaviour inside a scope: `city()` picks a town consistent with an already-fixed area, otherwise
picks freely and sets the area and nation from the town's tags. `postcode()` and `phoneNumber()`
build from the fixed area if one exists; if neither `city()` nor a caller-supplied `with(...)` has
fixed one yet, whichever of them runs first *establishes* it via `computeIfAbsent`, picking a real
town from the country's own dictionary (ignoring the town's name — only its area/nation tags) so a
later `city()` call is guaranteed a match, not an arbitrary or fabricated area. `isActive()` is
what lets `postcode()`/`phoneNumber()` tell "nothing fixed, but inside a real scope" (worth
establishing) apart from "outside any scope" (must not spend randomness on this at all, since it
would silently perturb every subsequent draw and break output stability, section 3.4) without
touching the record's attributes at all. `postcode()`'s outward code built this way still keeps
its inward code's impossible-letter guarantee (the 4.1 guarantee is unaffected either way);
`phoneNumber()` prefers a drama range matching the (fixed or newly established) place (e.g.
`020 7946 xxxx` for London) and falls back to the geography-neutral `01632 960xxx` range when no
matching drama range exists for that place — coherence is best-effort, fictionality is not.

Custom strategies join in the same way — e.g. a Companies House number strategy reads or resolves
`UK_NATION` and picks its prefix (`SC`, `NI`, none) accordingly, and conversely a strategy that
knows the country can `set` it for later fields.

**Interaction with `stored()`/`unique()`**: attributes steer *newly generated* candidates only.
A previously stored mapping is returned as-is and may predate the current record's attributes;
if strict coherence and stored mappings are both required, the caller must scope the store
accordingly. Documented caveat.

## 7. Extensibility

A client strategy is a lambda; binding it gives it every library feature:

```java
Strategy<String> nhsNumber = (in, ctx) -> generateNhsNumber(ctx.random());

Transformation<String> t = alterego.bind("myapp:nhs-number", nhsNumber).unique();
```

- The `domain` string (charset rules in section 2.6) namespaces randomness and storage; clients
  should prefix with their own namespace (`"myapp:..."`) to avoid clashing with built-ins
  (`"alterego:..."`).
- Non-`String` supported types are bound with a class token:
  `alterego.bind("myapp:case-ref", UUID.class, strategy)`.
- Composite strategies use `context.derived(subDomain, subInput)` to transform components
  consistently with the standalone transformations (guaranteed by the invariant in section 2.2).
- Persistent, cross-record relationships are maintained via `context.mappings()` (section 2.3);
  intra-record consistency via `context.record()` (section 6); never via static state in the
  strategy.
- Later (post-v1): a `ServiceLoader`-based mechanism for distributing strategy/dictionary packs
  as separate artifacts, e.g. additional countries.

## 8. Error handling

- `AlterEgoException` (unchecked) is the root. Subtypes:
  - `AlterEgoConfigException` — creation-time configuration errors (no resources for the
    country, unsupported value type, invalid domain, invalid options). Its subtype
    `AlterEgoPatternException` reports malformed patterns with the offending position.
  - `AlterEgoStoreException` — mapping store required but not configured, store failure, or a
    stored value that fails to decode.
  - `AlterEgoCollisionException` — `unique()` exhausted its retry budget.
  - `AlterEgoCoherenceException` — a record attribute was set to a value conflicting with the
    one already fixed for the record (section 6.2).
- Configuration errors surface when the transformation is created, never per element.
- Strategies never throw for ordinary data (empty strings, unparseable names); they degrade to a
  reasonable transformation (e.g. the `fullName()` surname fallback).

## 9. Project conventions

- **Java 25**, compiled with `--release 25`; idiomatic use of records, sealed interfaces, and
  pattern matching.
- **JPMS module** `org.identigon.alterego`; group id `org.identigon` (Maven
  Central-compatible with the GitHub account).
- **Gradle (Groovy DSL)**, `java-library` plugin, toolchain pinned to 25.
- **No runtime dependencies.** Test dependencies: JUnit Jupiter (and AssertJ if fluent assertions
  are wanted). No property-based-testing framework — property-style tests are plain JUnit loops
  over deterministically enumerated inputs (ADR 0013).
- Dictionaries and structural-rule tables are plain-text resource files, UTF-8, under
  `src/main/resources/dictionaries/<country>/<name>.txt` (ISO 3166-1 alpha-2, e.g. `GB`) —
  under `src/main/resources`, not the repo root, since they are loaded as classpath resources
  at runtime (`getResourceAsStream`), not read from the filesystem — each with a version and
  provenance header comment. One entry per line: the value, optionally followed by tab-separated
  tag fields (e.g. a town's postcode area and country). Lookup rules are
  defined in section 4.
- **Provenance header, required for every dictionary file derived from downloaded data** — a
  comment block naming: the source (organisation and dataset name), the exact original URL of
  the data as downloaded, the licence name and its exact original URL, and the retrieval date.
  The full licence text is additionally committed once per licence (not per file) under
  `src/main/resources/dictionaries/LICENCES/<licence-name>.txt`, referenced by name from each
  file's header — so
  redistribution terms are traceable without relying on an external link staying live. A
  dictionary file with no header, or one citing a licence with no matching committed text, fails
  the build-time well-formedness check (section 10).
- **Dictionary sourcing policy**: OGL, MIT, and CC0 are the acceptable licences; UK-government-
  associated sources (ONS, Ordnance Survey, National Records of Scotland, NISRA, Companies
  House) are preferred over others even where a non-government source offers broader coverage.
  The full policy and every dictionary's actual sourcing decision are tracked in
  `docs/dictionaries.md`.
- **Licence: MIT**, in a root file named `LICENCE` (UK spelling for the filename; the licence's
  own canonical text and title — "MIT License" — are left as written, since that is its
  official name). MIT covers the source code only; it does not relicense the bundled OGL data.
  `LICENCE`'s own text says so explicitly and points to `NOTICE`, so the split is clear to
  anyone reading the licence, not just to anyone who happens to notice a second file exists.
- **`NOTICE` file**, at the repository root, consolidating the exact required attribution
  string for every dictionary source in use. Attribution obligations under licences like OGL
  follow the data wherever it is redistributed — including transitively, since every application
  that depends on AlterEgo also redistributes the bundled dictionary data — so a per-file
  provenance header alone is not enough visibility.
- **Both `LICENCE` and `NOTICE` are packaged into `META-INF/` inside the built JAR** (a Gradle
  task on the `jar` task; not automatic) — most consumers receive only the JAR, never the
  repository, so both files must travel inside the artifact itself, not just sit at the repo
  root. `README.md` references both.

## 10. Testing strategy

- **Conformance vectors**: the Appendix A algorithms are enforced by committed test-vector files
  (JSON under `src/test/resources/vectors/`): derivation keys for known salt/domain/input/counter
  quadruples, raw stream bytes, sampling sequences, and hashed store keys. Any drift fails
  loudly. Vectors are generated by the first implementation, independently reviewed, then frozen
  for the major version.
- **Determinism**: property tests asserting `t.apply(x)` is stable across calls, across stream
  order permutations, and between sequential and parallel streams.
- **Golden outputs**: exact expected outputs of every built-in for a reference salt, to catch
  accidental algorithm/dictionary drift between releases.
- **Fictionality**: property tests assert every generated email uses a reserved domain, every
  UK phone number falls inside a published Ofcom drama range, every UK postcode violates the
  inward-code letter rules, and every surname/street-theme word is drawn from the authored
  fictional wordlist — over large generated samples. For the identifier built-ins (section 4.8),
  the same style of test asserts, per output: `nhsNumber()` matches `999 \d{3} \d{4}` **and**
  its mod-11 check digit verifies; `nationalInsuranceNumber()` matches `QQ \d{2} \d{2} \d{2} [A-D]`;
  `drivingLicenceNumber()` matches the A.7 shape with surname block `99999` and a valid
  date-of-birth encoding; `passportNumber()` matches `ZZ\d{7}`; `creditCardNumber()` matches
  `0\d{3}( \d{4}){3}` **and** its Luhn check digit verifies.
- **Locale equivalence**: `en-GB` and `cy-GB` configurations produce identical outputs for every
  v1 built-in (country-scoped resolution, section 4).
- **Record coherence**: within a scope, town/postcode/phone agree whichever field is asked
  first; keyed scopes resolve attributes independently of field order; two scopes never share
  state; outside a scope behaviour is byte-identical to pre-scope behaviour; conflicting `set`
  throws `AlterEgoCoherenceException`; a custom Companies House-style strategy coheres via
  `UK_NATION`.
- **Uniqueness**: exhaust a deliberately tiny output space (e.g. pattern `"D"` over 11 inputs)
  and assert `AlterEgoCollisionException`; concurrent hammer test against the in-memory store
  asserting no duplicate outputs and no lost mappings.
- **Store contract test**: a reusable test class exercising the `MappingStore` SPI (atomicity of
  `putIfAbsentUnique`, race behaviour), run against the in-memory store **and the file store**,
  and available to authors of external stores.
- **File store**: beyond the contract test — mappings and `unique()` collision resolutions
  survive `close()`/`open()`; a torn final line is ignored and safely overwritten; a malformed
  interior line, duplicate key, or wrong header fails `open` with `AlterEgoStoreException`; a
  second concurrent `open` of the same file fails; operations after `close()` fail; the file
  gains exactly one line per newly stored mapping and none for hits/rejections.
- **Dictionary coverage**: each shipped dictionary is non-empty, well-formed, its tag fields
  valid, and its provenance header present with a licence name matching a committed file under
  `dictionaries/LICENCES/` (build-time check; section 9). Deduplicated means no duplicate
  (value, tags) row, not no duplicate value: a tagged dictionary may legitimately repeat a value
  under different tags (e.g. London spans several UK postcode areas, so it appears once per
  area) — only an exact repeated row is rejected.
- **Null/edge cases**: null, empty string, single-character, and non-ASCII inputs for every
  built-in.

## Appendix A — Normative algorithms

Everything in this appendix is frozen within a major version and enforced by the conformance
vectors (section 10). An implementation is correct iff it reproduces the vectors byte-for-byte.

### A.1 Key derivation

```
message = utf8(purpose) || 0x00 || utf8(domain) || 0x00 || utf8(canonical) || 0x00 || uint32_be(counter)
key     = HMAC-SHA256(salt, message)
```

- `purpose` is `"alterego/1/random"` for randomness keys, `"alterego/1/mapkey"` for
  mapping-store keys, and `"alterego/1/record"` for keyed record-attribute resolution (where the
  `domain` slot carries the attribute name and the `canonical` slot carries the record key).
  Distinct uses of the same (salt, domain, input) never share a key.
- `domain` matches `[A-Za-z0-9:._-]{1,100}` (section 2.6); with purpose and domain NUL-free and
  the counter fixed-length at the end, the message is unambiguous even if `canonical` contains
  NUL characters.
- `counter` is a 4-byte big-endian unsigned integer: `0` normally; the retry counter for
  `unique()` re-derivation. Store keys and record-attribute keys always use counter `0`.
- A `char[]` salt is converted to bytes via UTF-8 before use.
- `derived(subDomain, subInput)` performs exactly this derivation with counter `0` — identical to
  a top-level transformation of `subInput` under `subDomain`.

### A.2 Randomness stream

The byte stream for a key is the concatenation of

```
block(i) = HMAC-SHA256(key, uint32_be(i))        i = 0, 1, 2, ...
```

consumed left to right, 32 bytes per block, generating blocks lazily.

### A.3 Sampling primitives

All primitives consume from the stream in the order the strategy calls them.

- `next8()` (internal): consume the next 8 bytes as a big-endian `long`.
- `nextLong(bound)`: require `bound > 0`. Rejection sampling over 63-bit draws:

  ```
  limit = (Long.MAX_VALUE / bound) * bound          // largest multiple of bound <= 2^63 - 1
  do { v = next8() & Long.MAX_VALUE } while (v >= limit)
  return v % bound
  ```
- `nextInt(bound)`: `(int) nextLong(bound)` with the same precondition.
- `nextBoolean()`: `nextLong(2) == 1`.
- `pick(list)`: `list.get(nextInt(list.size()))`; empty list throws `IllegalArgumentException`.
- `digit()`: `(char) ('0' + nextInt(10))`.
- `letterUpper()`: `(char) ('A' + nextInt(26))`; `letterLower()`: `(char) ('a' + nextInt(26))`.
- Pattern token `A`: `k = nextInt(52)`; `k < 26 ? (char) ('A' + k) : (char) ('a' + k - 26)`.
- Jitter shift over `[-n, +n]`: `nextLong(2n + 1) - n`.

### A.4 Mapping-store keys

The stored key for an input is the A.1 derivation with purpose `"alterego/1/mapkey"` and counter
`0`, encoded as 64 lowercase hexadecimal characters. This encoding is part of the persistent
store format and never changes within a major version.

The identifier algorithms below (A.5-A.9) compose the A.3 primitives; they are pinned by the
golden-output tests (section 10) rather than by new vector files. Draws always occur in exactly
the order written.

### A.5 NHS number generation

```
repeat:
    d[1..6] = digit() x 6                       // six fresh draws on every iteration
    digits  = 9, 9, 9, d1, d2, d3, d4, d5, d6   // the nine payload digits
    sum     = Σ digits[i] * w[i]                // w = 10, 9, 8, 7, 6, 5, 4, 3, 2
    c       = 11 - (sum mod 11)
    if c == 11: c = 0
until c != 10                                    // 10 marks an unissuable number: redraw
output "999 " + d1 d2 d3 + " " + d4 d5 d6 + c    // "999 ddd dddc"
```

### A.6 National Insurance number generation

```
d[1..6] = digit() x 6
s       = pick(["A", "B", "C", "D"])
output "QQ " + d1 d2 + " " + d3 d4 + " " + d5 d6 + " " + s     // "QQ dd dd dd S"
```

### A.7 GB driving licence number generation

```
decade   = digit()                       // second digit of the birth year
female   = nextBoolean()
month    = nextInt(12) + 1               // 1..12; add 50 if female
day      = nextInt(28) + 1               // 1..28 (fixed cap: no month-length logic)
yearUnit = digit()                       // final digit of the birth year
i1, i2   = letterUpper() x 2             // initials block
t1, t2   = letterUpper() x 2             // trailing letters
output "99999"                           // surname block: impossible on a real licence
     + decade + 2dig(month + (female ? 50 : 0)) + 2dig(day) + yearUnit
     + i1 + i2 + "9" + t1 + t2           // char 14 is the literal digit 9
```

`2dig(n)` is `n` zero-padded to two digits. The output is 16 characters, unspaced.

### A.8 UK passport number generation

```
d[1..7] = digit() x 7
output "ZZ" + d1 d2 d3 d4 d5 d6 d7       // "ZZddddddd", unspaced
```

### A.9 Credit card number generation

```
d[1..14] = digit() x 14
payload  = 0, d1, ..., d14               // fifteen digits, leading 0 fixed
sum = 0
for i = 0 .. 14:                          // i counts from the RIGHT end of payload
    v = payload[14 - i]                   // 0-indexed left to right
    if i is even: v = 2 * v; if v > 9: v = v - 9
    sum += v
c = (10 - (sum mod 10)) mod 10            // standard Luhn check digit
output the sixteen digits payload + c, grouped "0ddd dddd dddd dddc"
```
