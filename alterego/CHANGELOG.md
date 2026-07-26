# Changelog

All notable changes to AlterEgo are recorded here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Output-stability guarantees

These hold for every release within a major version (spec section 3.4), and are what make an
entry in this file meaningful rather than just a feature list:

- The Appendix A algorithms (key derivation, the HMAC counter-mode stream, sampling primitives,
  mapping-store key hashing) and the canonical value encodings (section 2.6) are frozen. Any
  change to either is a breaking change, called out explicitly here, not silently released.
- The conformance vectors under `src/test/resources/vectors/` are frozen: they were generated
  once from the reference implementation, independently reviewed, and are never regenerated. A
  future implementation change that would alter any vector value fails the build.
- Dictionary contents (names, towns, streets, organisation components, phone ranges) are
  versioned; a change to any of them is a breaking change to existing outputs and is called out
  here, not treated as a routine data update.
- The `GoldenOutputsTest` suite pins exact expected outputs for a reference salt across every
  built-in, to catch accidental drift in the algorithms or dictionaries between releases.

## [0.1.0] — 2026-07-26 (unreleased)

Initial implementation, milestones M0-M6 of `PLAN.md`. Not yet published to Maven Central.

### Added

- Deterministic pseudonymisation core: per-input HMAC-SHA256 key derivation, counter-mode
  randomness stream, sampling primitives (Appendix A), frozen conformance vectors.
- Pattern-based (`pattern()`), constant (`constant()`), and masking (`mask()`) transformations.
- Name and address built-ins: `firstName()`, `lastName()`, `fullName()`, `city()`,
  `streetAddress()`, `postcode()`, `organisationName()`, backed by curated, provenance-tracked UK
  dictionaries (`docs/dictionaries.md`).
- Temporal jitter: `shiftDate(...)`/`shiftDateTime(...)`, sixteen methods across eight jitter
  strategies, with inclusive `JitterOptions` clamping.
- Fictional-by-default contact details: `emailAddress()` (RFC 2606 reserved domains) and
  `phoneNumber()` (Ofcom drama ranges, `docs/phone-ranges.md`), each with a `realistic()` opt-out.
- `MappingStore` SPI, `InMemoryMappingStore`, and the `stored()`/`unique()` decorators, with the
  full section 2.5 decorator algebra and a reusable store contract test.
- Record coherence: `RecordScope` (anonymous and keyed), `RecordAttributes`, and built-in
  coherence between `city()`/`postcode()`/`phoneNumber()` via `UK_POSTCODE_AREA`/`UK_NATION` —
  whichever of the three runs first in a scope establishes the record's place for the others to
  follow.
- Extensibility: any `Strategy<T>` lambda bound via `AlterEgo.bind(...)` gets full built-in
  parity (determinism, `unique()`, `stored()`, record coherence, `derived(...)` composition).
- `maven-publish` configuration (group `io.github.dconneely`, artifact `alterego`).
