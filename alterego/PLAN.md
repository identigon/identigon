# AlterEgo — Implementation Plan

v0.1.0 (milestones M0 through M6) is complete and tagged.
`SPECIFICATION.md` is the authoritative behavioural contract and `CHANGELOG.md` covers what
shipped; the milestone-by-milestone build history that used to live in this file and in
`docs/tasks/M<n>.md` is preserved in git history rather than duplicated here.

## v0.2.0 (released)

Two milestones, specified in `SPECIFICATION.md` and decided in ADRs 0011/0012 before
implementation:

- **M7 — file-backed mapping store**: `FileMappingStore` (spec 5.4, ADR 0011), making
  `stored()`/`unique()`'s cross-run stability achievable out of the box.
- **M8 — identifier built-ins**: `nhsNumber()`, `nationalInsuranceNumber()`,
  `drivingLicenceNumber()`, `passportNumber()`, `creditCardNumber()` (spec 4.8, algorithms
  A.5-A.9, ADR 0012), each shape/checksum-valid inside a pinned fictional value space.

## v0.3.0 (in progress)

Landed on `main` (see `CHANGELOG.md`), not yet tagged:

- URL/domain built-ins: `domainName()` and `url()` (spec 4.1), drawing on RFC 2606 reserved
  domains and RFC 6761 reserved TLDs (`.test`/`.example`/`.invalid`).
- `Instant` jitter: `shiftInstant(...)` (spec 4.5), completing the `shiftDate`/`shiftDateTime`
  family for the already-supported `Instant` value type.
- Non-geographic phone ranges behind `PhoneOptions.includeNonGeographic()` (spec 4.1) — the
  Ofcom freephone/premium-rate/UK-wide drama ranges, excluded from the default pool.
- Extra canonical value types: `LocalTime`, `YearMonth`, `BigDecimal` (spec 2.6).
- Salt lifecycle: `AlterEgo` is `AutoCloseable`; `close()`/`destroy()` zero the salt, and both
  factory calls and transformation application on a closed instance throw (spec 2, "Lifecycle").
- Schema-preserving `redact(Class<T>)` and full-length `mask(char)` (spec 4.7).

## Known bugs

- (None)

## Deferred (not yet scheduled)

- `ServiceLoader` strategy/dictionary packs; additional countries.
- Language-sensitive generation keyed on the locale's language component (unused so far).
- Pattern extensions: `[ABC]` classes, `D{5}` repetition. (A generic checksum token is rejected,
  not deferred — ADR 0012.)
- External `MappingStore` modules (JDBC, Redis) as separate artifacts — built against the M4
  contract test. A local single-process file store ships in core as of v0.2.0 (ADR 0011).
- A public codec mechanism / SPI for caller-supplied value types (the fixed set is rejected as an
  SPI, ADR 0003) — only if a real need appears.
- Fictional-range additions: TEST-NET IPs (RFC 5737).
- `companyNumber()` (Companies House) — **blocked, unsolved fictional space.** No reserved/test
  range and no checksum exist; the only impossible value is zero, and mapping every company to
  zero is redaction, not pseudonymisation. A high range is time-dependent (Scotland is already at
  `SC770005`). Deferred until a reserved or never-issued range is found. Full analysis in
  `docs/fictional-ranges.md`. Note it has a regional element (`SC`/`NI`/plain) that would feed
  `UK_NATION` record coherence if it returns.


