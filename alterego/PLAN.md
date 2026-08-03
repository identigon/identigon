# AlterEgo — Implementation Plan

v0.1.0 (milestones M0 through M6) is complete and tagged.
`SPECIFICATION.md` is the authoritative behavioural contract and `CHANGELOG.md` covers what
shipped; the milestone-by-milestone build history that used to live in this file and in
`docs/tasks/M<n>.md` is preserved in git history rather than duplicated here. Released and
in-progress versions live in `CHANGELOG.md`; this file tracks only the forward-looking backlog.

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
