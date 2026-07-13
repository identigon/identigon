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
- `CLAUDE.md` for implementation agents: build/test commands, definition of done ("`./gradlew
  build` green, no new public API without a spec section"), and hard invariants (never change
  Appendix A algorithms, canonical encodings, frozen test vectors, or built-in domain names).
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
                                       AlterEgoAttributes, GbCountry, exceptions
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

In parallel: identify freely licensed sources for the GB-wide dictionaries (first names,
surnames, towns, streets) — GB-wide means including Welsh, Scottish, and Northern Irish names
and places as part of the real GB distribution, since a Welsh-language deployment is GB-wide
data, not Wales-located data. Record provenance decisions before any dictionary work in M2.
Dictionary data files are human-reviewed artifacts, not machine-collected ones.

**Done when**: conformance vectors pass; property tests prove order-independence and
parallel-stream stability; golden-output tests pin exact values for a reference salt; malformed
patterns, unsupported types, invalid domains, and short salts fail fast with good messages.

## M2 — Dictionaries + name/organisation/address strategies

- Dictionary resource format (UTF-8, one entry per line, version + provenance header), loader
  with caching and the section 4 lookup rule (all resources resolve by the locale's country;
  no country or no matching resources → fail fast), and a build-time well-formedness check
  (non-empty, deduplicated, sorted).
- Commit the `GB` dictionaries from the sources identified in M1: first names, surnames, street
  names (both suffix-form English and prefix-form Welsh entries), towns/cities,
  organisation-name components — all GB-wide pools, English-language forms. Town entries carry
  tab-separated postcode-area and UK-country tags (spec section 6.3), validated at build time.
- `firstName()`, `lastName()` (+ `preserveInitial()` option with its no-matching-initial
  fallback), `organisationName()` (legal-suffix preservation, including the Welsh company forms
  "Cyf." and "c.c.c." for country GB), `city()`, `streetAddress()`, `postcode()` (per-country
  format table; GB enforces the impossible-inward-code fictionality guarantee, with
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

- `jitterLocalDate(n, unit)`, `jitterLocalDateTime(n, unit)` with unit-compatibility validation
  at call time and `JitterOptions` (exclude-zero per A.3; inclusive `min`/`max` clamp bounds
  typed to the value — explicit values, the library never reads the clock, ADR 0007; pinned
  fields).
- `emailAddress()` (class-wise local-part replacement, last-`@` split rule, RFC 2606 reserved
  domains by default, options for preserving/mapping the domain).
- `phoneNumber()` (punctuation/grouping preserved; output lands in the country's reserved
  fictional range by default — Ofcom drama ranges for GB — with `PhoneOptions.realistic()`
  opt-out; countries without ranges fall back with no guarantee). The fictional range tables
  live in the per-country resources alongside the dictionaries.

**Done when**: jitter is proven uniform-in-range and deterministic; equal inputs jitter
identically; clamps, exclude-zero, and incompatible units behave at the boundaries; fictionality
property tests pass (reserved email domains, GB drama phone ranges, impossible GB postcodes
from M2).

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
- GB built-in coherence (spec section 6.3): `AlterEgoAttributes.GB_POSTCODE_AREA` /
  `GB_COUNTRY`; `city()` reads/sets them from the M2 town tags; `postcode()` builds its outward
  code from the fixed area; `phoneNumber()` prefers a place-matching drama range, falling back
  to `01632 960xxx`.
- A custom-strategy coherence example as a test (Companies House-style prefix from
  `GB_COUNTRY`).

**Done when**: the spec section 10 record-coherence tests pass — town/postcode/phone agree
whichever field runs first; keyed scopes are field-order-independent for resolved attributes;
scopes are isolated from each other; outside-scope outputs are byte-identical to M4 golden
outputs; fictionality property tests still pass inside scopes.

## M6 — Documentation and release readiness

- `README.md`: quick start, the determinism model in one paragraph, the "this is pseudonymisation,
  not anonymisation — guard the salt" warning including the frequency and low-cardinality limits
  (spec section 3.3), the fictional-by-default guarantee table, the `unique()` order-independence
  caveat, a record-coherence example, and an extension example. README code samples are compiled
  as tests so they cannot rot.
- Javadoc for the public API; `./gradlew javadoc` warning-free.
- `maven-publish` configuration (publishing itself deferred until licence and group id are
  confirmed — see the spec's open questions).
- CHANGELOG noting the output-stability guarantees (spec section 3.4) and the frozen vectors.

## Deferred (post-v1)

- `ServiceLoader` strategy/dictionary packs; additional countries (starting with `US`: its
  `555-01xx` fictional phone range and ZIP format).
- Language-sensitive generation keyed on the locale's language component (unused in v1).
- Pattern extensions: `[ABC]` classes, `D{5}` repetition, checksum-aware (Luhn) generation.
- External `MappingStore` modules (JDBC, file-backed) — built against the M4 contract test.
- Tagged name dictionaries (e.g. gendered name lists) — town dictionaries already carry
  structural tags in v1.
- Additional value types (`LocalTime`, `YearMonth`, ...) or a public codec mechanism, if a real
  need appears.
- `jitterInstant(n, unit)` — `Instant` is already a supported value type; only the built-in is
  missing, and it was not needed for v1.
- Fictional-range additions: TEST-NET IPs (RFC 5737), `.test`/`.invalid` domains (RFC 6761),
  never-allocated UK National Insurance prefixes.
