# AlterEgo — Implementation Plan

v0.1.0 (milestones M0 through M6) is complete and tagged.
`SPECIFICATION.md` is the authoritative behavioural contract and `CHANGELOG.md` covers what
shipped; the milestone-by-milestone build history that used to live in this file and in
`docs/tasks/M<n>.md` is preserved in git history rather than duplicated here.

## v0.2.0 (in progress)

Two milestones, specified in `SPECIFICATION.md` and decided in ADRs 0011/0012 before
implementation; each has an ordered checklist in `docs/tasks/`:

- **M7 — file-backed mapping store** (`docs/tasks/M7.md`): `FileMappingStore` (spec 5.4,
  ADR 0011), making `stored()`/`unique()`'s cross-run stability achievable out of the box.
- **M8 — identifier built-ins** (`docs/tasks/M8.md`): `nhsNumber()`,
  `nationalInsuranceNumber()`, `drivingLicenceNumber()`, `passportNumber()`,
  `creditCardNumber()` (spec 4.8, algorithms A.5-A.9, ADR 0012), each shape/checksum-valid
  inside a pinned fictional value space.

## Deferred (post-v0.2.0)

- `ServiceLoader` strategy/dictionary packs; additional countries.
- Language-sensitive generation keyed on the locale's language component (unused so far).
- Pattern extensions: `[ABC]` classes, `D{5}` repetition. (A generic checksum token is rejected,
  not deferred — ADR 0012.)
- External `MappingStore` modules (JDBC, Redis) as separate artifacts — built against the M4
  contract test. A local single-process file store ships in core as of v0.2.0 (ADR 0011).
- Tagged name dictionaries (e.g. gendered name lists) — town dictionaries already carry
  structural tags in v1.
- Additional value types (`LocalTime`, `YearMonth`, ...) or a public codec mechanism, if a real
  need appears.
- Jitter for `Instant` — already a supported value type; only the built-in (matching the
  `shiftDate`/`shiftDateTime` family, section 4.5) is missing, and it was not needed for v1.
- Fictional-range additions: TEST-NET IPs (RFC 5737), `.test`/`.invalid` domains (RFC 6761).
- `phoneNumber()`'s freephone/premium-rate/UK-wide Ofcom ranges, behind an option — excluded
  from v1's default pool since they don't read as a realistic personal contact number; already
  sourced and recorded in full (`docs/phone-ranges.md`), so adding them later needs no
  re-sourcing.
