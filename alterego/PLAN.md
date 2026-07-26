# AlterEgo — Implementation Plan

Milestones are ordered so that every stage leaves a working, testable library. The core
determinism machinery comes first because everything else depends on it and it is the hardest
part to change later. Dictionary sourcing — the main external (licensing) risk — starts in
parallel with M1 rather than waiting for M2.

Each milestone gets a `docs/tasks/M<n>.md` checklist (ordered, file-level steps with acceptance
commands) written before the milestone starts. Together with the artifacts in M0/M1, these are
what let a less-capable implementation agent execute the plan without design latitude.

## M0 — Project scaffold and implementer guardrails

- Gradle wrapper (current Gradle 9.x), Groovy DSL, `java-library` plugin, Java 25 toolchain.
- `module-info.java` for `io.github.dconneely.alterego`.
- Test setup: JUnit Jupiter + jqwik.
- GitHub Actions CI: build + test on every push.
- `docs/adr/` with short records of the decisions already made and why, so they are not
  "helpfully" reverted: per-input derivation; no `RandomGenerator` in the API and no
  off-the-shelf PRNG (e.g. Mersenne Twister) behind it — the HMAC counter-mode stream uses the
  full 256-bit derived key, keeps the security argument a pure-PRF one, and avoids freezing any
  JDK or third-party algorithm into the output-stability contract; fixed value-type set instead
  of a codec SPI; atomic `putIfAbsentUnique` instead of reserve-then-put; fictional by default;
  fixed default locale (`Locale.UK`), never `Locale.getDefault()`; explicit clamp bounds — the
  library never reads the clock; record coherence via `RecordScope`, separate from the
  `MappingStore`.
- `.gitignore`, `README.md` stub pointing at `SPECIFICATION.md`.
- Package layout:

```
io.github.dconneely.alterego           AlterEgo, Transformation, Strategy,
                                       TransformationContext, Randomness, Mappings, NullPolicy,
                                       RecordScope, AttributeKey, RecordAttributes,
                                       AlterEgoAttributes, UkNation, exceptions
io.github.dconneely.alterego.store     MappingStore, InMemoryMappingStore
io.github.dconneely.alterego.strategy  built-in strategies (mostly package-private,
                                       exposed only via AlterEgo factory methods)
io.github.dconneely.alterego.pattern   pattern compiler
```

**Done when**: `./gradlew build` passes locally and in CI with one placeholder test.

## M1 — API stubs, determinism core, pattern strategies

The derivation scheme, `Randomness`, the context, and the binding machinery — plus the pattern
strategies, because they exercise the core without needing dictionaries.

Order matters within this milestone:

1. **Public API stubs first**: every public type from the spec (sections 2, 5.1, 6, 8), fully
   Javadoc'd, compiling, methods throwing `UnsupportedOperationException`. This freezes the API
   shape before any implementation and gives later work a fill-in-the-blanks structure.
2. **Appendix A implementation**: key derivation (A.1, including purpose tags and the retry
   counter), HMAC counter-mode stream (A.2), sampling primitives (A.3), store-key hashing (A.4).
3. **Conformance vectors**: generate JSON vectors from the implementation
   (`src/test/resources/vectors/`), review them by hand against Appendix A (at minimum,
   independently recompute a handful of HMAC values), then freeze them; the conformance test
   loads and asserts them from then on.
4. `TransformationContext` + `Mappings` view; the outside-scope no-op `record()` implementation
   (spec section 6.2 — scoped behaviour lands in M5); `derived(subDomain, subInput)` with a test
   proving the section 2.2 invariant (derived context ≡ top-level context for the same
   domain/input).
5. Value-type registry (internal): canonical encodings per section 2.6; fail-fast rejection of
   unsupported types and invalid domains at bind time.
6. `AlterEgo` + builder (salt ≥ 16 bytes, default `Locale.UK`, optional mapping store,
   `NullPolicy`); `Transformation<T>` binding with the decorator algebra of section 2.5
   (`unique()`/`stored()` throw `AlterEgoStoreException` stubs until M4).
7. Pattern compiler (`D`, `L`, `l`, `A`, `\` escape, literals) with position-reporting
   `AlterEgoPatternException`; `pattern(String)`, `constant(T)`, `mask(char, int)`.

In parallel: identify freely licensed sources for the UK-wide dictionaries (first names,
surnames, towns, streets) — UK-wide means including Welsh, Scottish, and Northern Irish names
and places as part of the real distribution. Record provenance decisions before dictionary work.
Dictionary data files are independently reviewed artifacts, not machine-collected ones.
Every downloaded dataset actually used needs, recorded before consuming it: full attribution
(source organisation and dataset name), the exact original URL of the data, the licence name
and its exact original URL, and the retrieval date — spec section 9 pins this as a per-file
provenance header plus a once-per-licence committed licence text under `dictionaries/LICENCES/`.

**Done when**: conformance vectors pass; property tests prove order-independence and
parallel-stream stability; golden-output tests pin exact values for a reference salt; malformed
patterns, unsupported types, invalid domains, and short salts fail fast with good messages.

## M2 — Dictionaries + name/organisation/address strategies

- Dictionary resource format (UTF-8, one entry per line, version + provenance header per spec
  section 9: source, original data URL, licence name and URL, retrieval date), loader
  with caching and the section 4 lookup rule (all resources resolve by the locale's country;
  no country or no matching resources → fail fast), and a build-time well-formedness check
  (non-empty, deduplicated, sorted, provenance header present, cited licence text committed
  under `dictionaries/LICENCES/`).
- Commit the dictionaries from the sources identified in M1: first names, surnames, street
  names (composed from theme-word and type-word pools), towns/cities,
  organisation-name components — all UK-wide pools. Town entries carry
  tab-separated postcode-area and nation tags (spec section 6.3), validated at build time.
- **Done**: root `LICENCE` (MIT, scoped to source code, points at `NOTICE` for the separately
  licensed OGL data) and `NOTICE` (every dictionary source's exact required attribution string,
  for sources actually in use) created; both packaged into `META-INF/` in the built JAR via a
  Gradle task on `jar` (verified against the built artifact); update `NOTICE` whenever a sourcing
  decision in `docs/dictionaries.md` changes.
- `firstName()`, `lastName()` (+ `preserveInitial()` option with its no-matching-initial
  fallback), `organisationName()` (legal-suffix preservation, including the Welsh company forms
  "Cyf." and "c.c.c."), `city()`, `streetAddress()`, `postcode()` (per-country
  format table; enforces the impossible-inward-code fictionality guarantee, with
  `PostcodeOptions.realistic()` opt-out). Factory methods fail fast when the locale has no
  country or the country has no resources.
- `fullName()` implementing the pinned tokenisation rules of section 4.2 (including hyphenated
  tokens and the surname fallback).

**Done when**: cross-consistency test passes (`fullName("Alice Smith")` parts equal
`firstName("Alice")` / `lastName("Smith")`); the locale-equivalence test passes (`en-GB` and
`cy-GB` produce identical outputs for every built-in); tokenisation rule tests cover middle
names, hyphens, and single tokens; non-ASCII and edge-case inputs covered; missing locale or
country fails at factory-method call time.

## M3 — Temporal jitter + contact details

- `shiftDate(int days)`, `shiftDate(AlterEgo.DateField)`, and the six `shiftDateTime(...)`
  overloads pairing a date strategy with a time strategy (spec section 4.5), each with a twin
  taking a trailing `JitterOptions<T>` for inclusive `min`/`max` clamp bounds typed to the value —
  explicit values, the library never reads the clock, ADR 0007.
- `emailAddress()` (class-wise local-part replacement, last-`@` split rule, RFC 2606 reserved
  domains by default, options for preserving/mapping the domain).
- `phoneNumber()` (punctuation/grouping preserved; output lands in the country's reserved
  fictional range by default — Ofcom drama ranges — with `PhoneOptions.realistic()`
  opt-out; countries without ranges fall back with no guarantee). The fictional range tables
  live in the per-country resources alongside the dictionaries.

**Done when**: every jitter strategy is proven uniform-in-range and deterministic; equal inputs
jitter identically; clamps behave at the boundaries; nanoseconds are zeroed in every
`shiftDateTime(...)` output; fictionality property tests pass (reserved email domains, drama
phone ranges, impossible postcodes from M2).

## M4 — MappingStore, `stored()`, `unique()`

- `MappingStore` SPI (`get`, `putIfAbsent`, atomic `putIfAbsentUnique` returning the sealed
  `PutUniqueResult`) and `InMemoryMappingStore` (ConcurrentHashMap + inverse index per
  namespace).
- Hashed-key storage per A.4 by default (raw-key opt-in); decode failures raise
  `AlterEgoStoreException`.
- `stored()` decorator; `unique()` decorator with retry counter, configurable max attempts, the
  three-outcome loop from spec section 5.3, and the decorator algebra of section 2.5.

**Done when**: tiny-output-space test raises `AlterEgoCollisionException`; a multi-threaded hammer
test over the in-memory store shows no duplicate outputs and no lost mappings; the reusable store
contract test (usable by future external implementations) covers atomicity of
`putIfAbsentUnique`.

## M5 — Record coherence

- `RecordScope` (anonymous and keyed), `AttributeKey`, `RecordAttributes` with the spec
  section 6.2 semantics: first-touch-wins, conflicting `set` →
  `AlterEgoCoherenceException`, no-op behaviour outside a scope, keyed-scope `computeIfAbsent`
  randomness derived per Appendix A.1 (purpose `alterego/1/record`).
- `TransformationContext.record()` wiring through the binding machinery and `derived(...)`.
- Built-in coherence (spec section 6.3): `AlterEgoAttributes.UK_POSTCODE_AREA` /
  `UK_NATION`; `city()` reads/sets them from the M2 town tags. `postcode()`/`phoneNumber()` build
  from the fixed area if one exists, or — via `RecordAttributes.isActive()` and a shared
  `computeIfAbsent`-based establishing helper (ADR 0009) — pick a real town from the country's
  dictionary and establish it themselves if they're the first field to touch the record's place,
  guaranteeing a later `city()` call always matches. `phoneNumber()`
  falls back to `01632 960xxx` only when the (fixed or newly established) area has no drama range
  of its own.
- A custom-strategy coherence example as a test (Companies House-style prefix from
  `UK_NATION`).

**Done when**: the spec section 10 record-coherence tests pass — town/postcode/phone agree
whichever field runs first; keyed scopes are field-order-independent for resolved attributes;
scopes are isolated from each other; outside-scope outputs are byte-identical to M4 golden
outputs; fictionality property tests still pass inside scopes.

## M6 — Documentation and release readiness

- `README.md`: quick start, the determinism model in one paragraph, the "this is pseudonymisation,
  not anonymisation — guard the salt" warning including the frequency and low-cardinality limits
  (spec section 3.3), the fictional-by-default guarantee table, the `unique()` order-independence
  caveat, a record-coherence example, and an extension example. README code samples are compiled
  as tests so they cannot rot. Also: a licence section (MIT, `LICENCE`) and a data-attribution
  section pointing at `NOTICE`, stating that dictionary data is OGL-licensed UK government
  data and that this obligation passes through to applications depending on AlterEgo (spec
  section 9; `docs/dictionaries.md`).
- Javadoc for the public API; `./gradlew javadoc` warning-free.
- `maven-publish` configuration (group id `io.github.dconneely`, licence MIT, `LICENCE` — see
  spec section 9).
- CHANGELOG noting the output-stability guarantees (spec section 3.4) and the frozen vectors.

## Deferred (post-v1)

- `ServiceLoader` strategy/dictionary packs; additional countries.
- Language-sensitive generation keyed on the locale's language component (unused in v1).
- Pattern extensions: `[ABC]` classes, `D{5}` repetition, checksum-aware (Luhn) generation.
- External `MappingStore` modules (JDBC, file-backed) — built against the M4 contract test.
- Tagged name dictionaries (e.g. gendered name lists) — town dictionaries already carry
  structural tags in v1.
- Additional value types (`LocalTime`, `YearMonth`, ...) or a public codec mechanism, if a real
  need appears.
- Jitter for `Instant` — already a supported value type; only the built-in (matching the
  `shiftDate`/`shiftDateTime` family, section 4.5) is missing, and it was not needed for v1.
- Fictional-range additions: TEST-NET IPs (RFC 5737), `.test`/`.invalid` domains (RFC 6761),
  never-allocated National Insurance prefixes.
- `phoneNumber()`'s freephone/premium-rate/UK-wide Ofcom ranges, behind an option — excluded
  from v1's default pool since they don't read as a realistic personal contact number; already
  sourced and recorded in full (`docs/phone-ranges.md`), so adding them later needs no
  re-sourcing.
- A `QT` ("Cute") locale: a wholly invented country whose dictionaries (first names, surnames,
  towns, street names, organisation components) are authored, not sourced — deliberately
  obvious, unmistakably fictitious values ("Madeupborough", "Unrealtown"). Unlike
  real-word dictionaries, this would give names/streets/organisations the same kind of
  guaranteed-fictional property ADR 0005 gives phone/email/postcode, since invented words can
  never coincide with something real, the way rare-but-real ones still could. `QT` sits in ISO
  3166-1's user-assigned range, distinct from `ZZ` (already used for internal test fixtures,
  not a shipped locale). Would need its own ADR, not a revision of ADR 0005 — that ADR's
  conclusion is specifically about real-word dictionaries and would stand unchanged.
