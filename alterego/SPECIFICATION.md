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
    .map(alterego.jitterLocalDate(30, ChronoUnit.DAYS))
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
- **Realistic output**: replacement names come from country-appropriate dictionaries;
  pattern-based output matches a caller-declared format.
- **Fictional by default**: where a recognised reserved-for-fiction or guaranteed-invalid value
  space exists (reserved email domains, Ofcom drama numbers, impossible postcodes), built-ins
  generate inside it, so pseudonymised data cannot accidentally reference something real
  (section 4.1).
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
    .build();
```

- **Salt**: required, minimum 16 bytes (shorter salts are trivially brute-forced; the builder
  rejects them). Accepted as `byte[]` or `char[]`; a `char[]` is converted via UTF-8.
- **Locale**: defaults to the fixed constant `Locale.UK` (`en-GB`) — this library's primary
  deployment. A *fixed* default is deterministic on every machine; what remains banned is
  `Locale.getDefault()`, which would tie output to machine configuration (ADR 0006). Non-UK
  users configure explicitly; an unshipped country fails fast (section 4). v1 built-ins consult
  only the locale's **country**; the language component steers nothing yet.

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
| `LocalDateTime`       | `toString()` — ISO-8601; note this omits zero seconds (`14:30`) and |
|                       | includes nanoseconds only when present; injective either way        |
| `Instant`             | `toString()` — ISO-8601 UTC                                         |
| `UUID`                | `toString()` — lower case                                           |
| any `enum`            | `name()`                                                            |

`bind(domain, type, strategy)` throws `AlterEgoConfigException` immediately for an unsupported
`type` (fail fast, not per element). Enums are recognised via `Class::isEnum`. Adding further
types later (e.g. `LocalTime`, `YearMonth`) is a non-breaking change; this set is the v1 floor.

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
  salt; similarly, a jittered date remains within `±n` units of the truth. Where this matters,
  the mitigation is aggregation or suppression — out of scope (section 1).
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
no resources for the locale's country, malformed pattern, incompatible jitter unit, invalid
options.

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
| `phoneNumber()`              | never a connectable number   | Ofcom drama ranges (e.g.              |
|                              |                              | `020 7946 0xxx`, `07700 900xxx`,      |
|                              |                              | `01632 960xxx`)                       |
| `postcode()`                 | never a deliverable postcode | plausible outward code, but the       |
|                              |                              | inward code ends in a letter never    |
|                              |                              | used in real postcodes (`C I K M O V`)|

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

Countries with no defined fictional range fall back to in-place digit replacement with **no
guarantee**; the Javadoc of each built-in states, per country, which category applies. No
guarantee is possible for names, streets, cities, or organisations — each output word is real
(that is what makes it realistic); only the combination and its attachment to a record are
synthetic. Candidate future additions in the same spirit: TEST-NET IP addresses (RFC 5737),
`.test`/`.invalid` domains (RFC 6761), and never-allocated UK National Insurance prefixes.

### 4.2 People and organisations

| Method               | Behaviour                                                       |
|----------------------|-----------------------------------------------------------------|
| `firstName()`        | Replacement drawn from the country's first-name dictionary.     |
| `lastName()`         | Replacement drawn from the country's surname dictionary.        |
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
|                   | street name composed from the country's dictionary (a theme word     |
|                   | plus a type word, e.g. "Victoria Road") — the dictionary, not the    |
|                   | code, owns the vocabulary.                                           |
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
alterego.jitterLocalDate(30, ChronoUnit.DAYS)      // Transformation<LocalDate>
alterego.jitterLocalDateTime(4, ChronoUnit.HOURS)  // Transformation<LocalDateTime>
```

- Shifts by a deterministic **whole number** of units drawn uniformly from `[-n, +n]`
  (Appendix A.3); smaller fields are untouched (jitter by hours preserves minutes and seconds).
  Units incompatible with the value type (e.g. `HOURS` for `LocalDate`) are rejected at call time.
- `JitterOptions` control:
  - `excludeZero()` — guarantee the value changes (Appendix A.3);
  - `min(value)` / `max(value)` — **inclusive** clamp bounds, typed to the transformation's value
    type. Clamping makes values near a bound pile up on it; documented. The library never reads
    the clock (section 3.4): a caller wanting "no future dates" writes
    `max(LocalDate.now())`. Note the type-dependent meaning of "past", which the caller owns:
    a `LocalDate` strictly in the past excludes today (`max(LocalDate.now().minusDays(1))`),
    whereas an instant strictly in the past is anything before now
    (`max(LocalDateTime.now().minusNanos(1))`).
  - pinning fields (e.g. jitter the day but preserve the year).
- Because the shift is derived from the input value, equal timestamps jitter identically —
  preserving equality relationships in the data.

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
for v1: character classes `[ABC]`, repetition counts `D{5}`, and checksum-aware generation
(e.g. Luhn digits).

There is deliberately no generic format-inferring transformation (section 1 non-goals):
consumers state the format they want. Built-ins that replace characters in place (email local
parts, phone digits) do so as documented per-transformation behaviour, not via a public
inference facility.

### 4.7 Utility

| Method                        | Behaviour                                                 |
|-------------------------------|------------------------------------------------------------|
| `constant(T value)`           | Replace everything with a fixed value.                     |
| `mask(char c, int keepLast)`  | Mask all but the last `keepLast` characters with `c`.      |
|                               | Inputs of length ≤ `keepLast` are returned unchanged;      |
|                               | negative `keepLast` throws `AlterEgoConfigException`.      |

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
  number of distinct inputs — documented; large or long-lived datasets belong in an external
  store. JDBC-, file-, or Redis-backed stores are left to clients or future modules; the SPI plus
  the contract test (section 10) are the contract.
- **Privacy**: by default the *key* written to the store is the purpose-separated
  `HMAC(salt, input)` from Appendix A.4, encoded as 64 lowercase hex characters — the store never
  contains raw input data, and store contents cannot be used to reconstruct randomness keys.
  Storing raw keys is opt-in for debugging. Keys are never decoded; stored *values* are decoded
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
4. After a configurable number of attempts (default 64), throw `AlterEgoCollisionException` —
   with a message pointing out the likely cause (output space too small for the input volume).

Uniqueness necessarily depends on the mapping store's lifetime: it holds across everything that
shares one store, and no further. This is documented rather than hidden.

**Order-independence caveat.** Undecorated transformations are fully order-independent
(section 3.1). `unique()` is the one necessary exception: when two inputs' natural candidates
collide, whichever input is processed *first* keeps the natural candidate and the other is
re-derived. Absent collisions — the overwhelmingly common case — `unique()` outputs are identical
regardless of processing order; on collision, only the colliding inputs are affected, and the
resolution is captured in the mapping store so it remains stable on every later run. This is
inherent to any uniqueness guarantee, and the documentation says so.

## 6. Record coherence

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
picks freely and sets the area and country from the town's tags; `postcode()` builds its outward
code from the fixed area (inward code stays impossible, so the 4.1 guarantee is unaffected);
`phoneNumber()` prefers a drama range matching the fixed place (e.g. `020 7946 xxxx` for London)
and falls back to the geography-neutral `01632 960xxx` range when no matching drama range exists
— coherence is best-effort, fictionality is not.

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
    country, unsupported value type, invalid domain, invalid options, incompatible jitter unit).
    Its subtype `AlterEgoPatternException` reports malformed patterns with the offending
    position.
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
- **JPMS module** `io.github.dconneely.alterego`; group id `io.github.dconneely` (Maven
  Central-compatible with the GitHub account). Open question: switch if a custom domain is
  preferred.
- **Gradle (Groovy DSL)**, `java-library` plugin, toolchain pinned to 25.
- **No runtime dependencies.** Test dependencies: JUnit Jupiter, and jqwik for property-based
  determinism tests.
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
  loudly. Vectors are generated by the first implementation, human-reviewed, then frozen for the
  major version.
- **Determinism**: property tests asserting `t.apply(x)` is stable across calls, across stream
  order permutations, and between sequential and parallel streams.
- **Golden outputs**: exact expected outputs of every built-in for a reference salt, to catch
  accidental algorithm/dictionary drift between releases.
- **Fictionality**: property tests assert every generated email uses a reserved domain, every
  UK phone number falls inside a published Ofcom drama range, and every UK postcode violates the
  inward-code letter rules — over large generated samples.
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
  `putIfAbsentUnique`, race behaviour), run against the in-memory store and available to authors
  of external stores.
- **Dictionary coverage**: each shipped dictionary is non-empty, well-formed, its tag fields
  valid, and its provenance header present with a licence name matching a committed file under
  `dictionaries/LICENCES/` (build-time check; section 9). Deduplicated means no duplicate
  (value, tags) row, not no duplicate value: a tagged dictionary may legitimately repeat a value
  under different tags (e.g. London spans several UK postcode areas, so it appears once per
  area) — only an exact repeated row is rejected.
- **Null/edge cases**: null, empty string, single-character, and non-ASCII inputs for every
  built-in.

## 11. Open questions

1. **Group id / package**: `io.github.dconneely.alterego` assumed; confirm.
2. ~~**Licence**: none chosen yet.~~ **Resolved: MIT** (section 9) — compatible with the OGL
   data dictionaries embed, since the two licences cover different things (code vs. data) and
   don't need merging; OGL's own guidance confirms compatibility with other open licences.
3. **Dictionary sourcing**: name/place lists must come from freely licensed sources — OGL, MIT,
   or CC0 preferred as clean, specific written licences; a bare public-domain claim is not
   automatically in the same tier (it is a copyright-status assertion, not a licence, and can be
   jurisdiction-dependent) unless it rests on genuinely expired copyright — see
   `docs/dictionaries.md`'s sourcing policy. Strong preference for UK-government-associated
   sources (ONS, Ordnance Survey, National Records of Scotland, NISRA, Companies House) over
   others even where a non-government source offers broader coverage; provenance to be recorded
   in each dictionary file's header (section 9) and tracked in `docs/dictionaries.md`. This is
   the main external risk, is investigated early (see the plan), and the data files should be
   human-reviewed rather than machine-collected. Town entries additionally need postcode-area
   and country tags (section 6.3).

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
- Jitter shift over `[-n, +n]`: `nextLong(2n + 1) - n`. With exclude-zero:
  `k = nextLong(2n)`; shift is `k - n` if `k < n`, otherwise `k - n + 1` (uniform over
  `[-n, -1] ∪ [1, n]`).

### A.4 Mapping-store keys

The stored key for an input is the A.1 derivation with purpose `"alterego/1/mapkey"` and counter
`0`, encoded as 64 lowercase hexadecimal characters. This encoding is part of the persistent
store format and never changes within a major version.
