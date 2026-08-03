# AlterEgo

AlterEgo is a zero-dependency Java 25 library for deterministic pseudonymisation. It replaces
personal or sensitive values (names, addresses, dates, reference numbers) with realistic-looking
substitutes, such that the same input always produces the same output for a given configuration.
It is designed for use in Java streams, is extensible with custom transformations, and generates
fictional-by-default output (reserved email domains, Ofcom drama phone numbers, impossible
postcodes, obviously-fictional surnames and street names) so pseudonymised data never accidentally
references something real.

See [`SPECIFICATION.md`](SPECIFICATION.md) for the full behavioural contract,
[`CHANGELOG.md`](CHANGELOG.md) for the release history and what's changed between versions, and
[`PLAN.md`](PLAN.md) for the deferred backlog.

Every code sample below is compiled and run as part of the test suite
(`src/test/java/org/identigon/alterego/ReadmeExamplesTest.java`), so they cannot silently
rot out of sync with the actual API.

## Quick start

```java
byte[] salt = loadSecretSaltFromSomewhereSafe(); // >= 16 bytes; treat it like a password

AlterEgo alterego = AlterEgo.builder()
    .salt(salt)
    .build();

var pseudonymisedFirstNames = originalFirstNames.stream()
    .map(alterego.firstName())
    .toList();

var pseudonymisedBirthDates = originalBirthDates.stream()
    .map(alterego.shiftDate(30))
    .toList();
```

Every `Transformation<T>` returned by `AlterEgo` is a `Function<T, T>`, so it drops straight into
`.map(...)`. Transformations are immutable and thread-safe: build one per built-in, reuse it
across every stream and thread you like.

## How determinism works

Every output is a function of the salt, a domain (the transformation's own namespace, e.g.
`alterego:first-name`), and the input value — nothing else. Concretely, an HMAC-SHA256 key is
derived from `(salt, domain, input)`, and that key drives a counter-mode byte stream the strategy
draws from. Two consequences fall out of that directly: the same input always produces the same
output, regardless of what order a stream processes elements in or whether it runs in parallel;
and two different transformations (different domains) of the same input never correlate, so
knowing one pseudonym reveals nothing about another. The library never reads the system clock,
`Locale.getDefault()`, or any other ambient state — the only inputs to any output are the ones
you configured explicitly.

## This is pseudonymisation, not anonymisation

**The salt is a secret.** Anyone holding it can confirm guesses about specific values (`does
"Alice Smith" map to this row?`) by recomputing the same transformation. Treat it exactly like a
credential: never log it, never commit it, and store it the way you'd store any other secret.

Determinism itself has two inherent limits, not bugs, and not something a different
implementation could avoid:

- **Frequency is preserved.** Equal inputs map to equal outputs, so the most common surname in
  your data is still the most common pseudonym in the output, and a jittered date stays close to
  the true one by construction (`shiftDate(30)` never moves a date by more than 30 days). An
  attacker with population statistics can make informed guesses about frequent values without
  ever seeing the salt. Where this matters, the mitigation is aggregation or suppression — outside
  this library's scope.
- **Low-cardinality values gain almost nothing.** Deterministically relabelling a `Boolean` or a
  small enum is just that: a relabelling of a handful of values. These types are supported so
  composite and custom strategies can cover whole records, not because pseudonymising a
  low-cardinality column in isolation meaningfully protects it.

## Fictional by default

Where a real reserved-for-fiction or structurally-impossible value space exists, the matching
built-in generates inside it by default, so pseudonymised data can never accidentally reference a
real mailbox, phone number, or deliverable address:

| Transformation      | Guarantee                            | Mechanism                                          |
|---------------------|---------------------------------------|-----------------------------------------------------|
| `emailAddress()`    | never a working mailbox               | RFC 2606 reserved domains (`example.com`, `.net`, `.org`) |
| `phoneNumber()`     | never a connectable number             | Ofcom drama ranges (e.g. `020 7946 0xxx`, `07700 900xxx`, `01632 960xxx`) |
| `postcode()`        | never a deliverable postcode           | a plausible outward code, but an inward code letter Royal Mail never uses |
| `nhsNumber()`       | never issued to a real person          | `999` prefix reserved for test and synthetic data |
| `nationalInsuranceNumber()` | never issued to a real person  | `QQ` prefix structurally unallocatable by HMRC |
| `drivingLicenceNumber()` | never issued to a real person     | `99999` surname block impossible on a real licence |
| `passportNumber()`  | never issued to a real person          | `ZZ` prefix impossible on a UK passport |
| `creditCardNumber()`| never issued to a real person          | `0` major industry identifier reserved by ISO/IEC 7812 |
| `lastName()`        | reads as obviously fictional, not a real person's surname | authored (not sourced) surname vocabulary (e.g. `"Testperson"`) |
| `streetAddress()`   | reads as obviously fictional, not a real street | authored (not sourced) theme word (e.g. `"Example"`) plus a real structural type word (`"Road"`) |

Each of the first eight is format-valid — that's exactly why it was reserved — so it passes
ordinary validation but fails a live lookup against real reference data (an MX record, a number
allocation, a delivery address, a PDS trace). Where full realism matters more than the guarantee,
`PhoneOptions.realistic()` and `PostcodeOptions.realistic()` opt out explicitly; their Javadoc
states the risk (a realistic output can coincide with a real person's number or address). The
identifier built-ins offer no realistic opt-out.

```java
Transformation<String> cc = alterego.creditCardNumber();
// 0814 6733 3628 4153 (valid Luhn check digit, but '0' MII is unissued)
```

`lastName()` and `streetAddress()` use a different mechanism: there's no officially reserved
"fictional surname" or "fictional street" space to draw from, so their vocabulary is authored
rather than sourced from real UK data, and reviewed to avoid coinciding with a known real name
(ADR 0010) — a curation-time guarantee, not a structural one. `firstName()` and `city()` are
unaffected and keep drawing from real data; the surname alone is enough to make a full name
unmistakably not a real person's.

## `unique()` and the order-independence caveat

```java
Transformation<String> uniqueCustomerId =
    alterego.bind("myapp:customer-id", customerIdStrategy).unique();
```

`unique()` guarantees distinct inputs never map to the same output, backed by a `MappingStore`
(configure one via `AlterEgo.builder().mappingStore(...)`). Every undecorated transformation is
fully order-independent — reordering, filtering, or deduplicating a stream never changes an
individual mapping. `unique()` is the one deliberate exception: when two inputs' natural
candidates would collide, whichever one is processed *first* keeps that natural candidate, and
the other is re-derived. Absent an actual collision — the overwhelming majority of real data —
`unique()` output is identical regardless of processing order; when a collision does happen, only
those specific inputs are affected, and the resolution is recorded in the mapping store, so it
stay stable on every later run.

To make this stability permanent, back AlterEgo with a persistent file store:

```java
try (FileMappingStore store = FileMappingStore.open(Path.of("mappings.alterego"))) {
    AlterEgo alterego = AlterEgo.builder().salt(salt).mappingStore(store).build();
    Transformation<String> customerId = alterego.pattern("LLDDDDDD").unique();
    // ... mappings and collision resolutions now survive across runs
}
```

## Record coherence

Transforming a record's fields independently can produce incoherent combinations — a town in the
north paired with a postcode and phone number that only exist in London. A `RecordScope` lets
related fields agree:

```java
try (RecordScope rec = alterego.record()) {
    outputRow.town     = rec.apply(alterego.city(),        inputRow.town);
    outputRow.postcode = rec.apply(alterego.postcode(),    inputRow.postcode);
    outputRow.phone    = rec.apply(alterego.phoneNumber(), inputRow.phone);
}
```

Whichever of those three fields runs first fixes the record's place; the others follow it. This
is best-effort coherence, not a fictionality guarantee — the guarantees in the table above hold
regardless of whether a `RecordScope` is in use.

## Extending with custom strategies

A custom strategy is a plain lambda; binding it gives it every feature a built-in has —
determinism, `unique()`, `stored()`, and record coherence:

```java
Strategy<String> employeeIdStrategy = (input, context) -> generateEmployeeId(context.random());

Transformation<String> employeeId =
    alterego.bind("myapp:employee-id", employeeIdStrategy).unique();
```

Prefix your own domains (`"myapp:..."`) to avoid clashing with AlterEgo's own built-in domains
(`"alterego:..."`).

## Licence

The source code is MIT-licensed — see [`LICENCE`](LICENCE).

## Data attribution

The bundled UK dictionary data (names, towns, streets, organisation-name components) is derived
from UK government sources published under the Open Government Licence v3.0, with one documented
exception (Ofcom's drama phone-number ranges — see [`docs/phone-ranges.md`](docs/phone-ranges.md)
for why). Full provenance for every source is tracked in
[`docs/dictionaries.md`](docs/dictionaries.md); the exact required attribution string for every
source in use is consolidated in [`NOTICE`](NOTICE), which — along with `LICENCE` — is packaged
into the built JAR's `META-INF/` directory. **This attribution obligation passes through
transitively to any application that depends on AlterEgo**, since the JAR bundles this data into
every application built on it, not just this repository.
