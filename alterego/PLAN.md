# AlterEgo — Implementation Plan

v1 (milestones M0 through M6) is complete. `SPECIFICATION.md` is the authoritative behavioural
contract and `CHANGELOG.md` covers what shipped; the milestone-by-milestone build history that
used to live in this file and in `docs/tasks/M<n>.md` is preserved in git history rather than
duplicated here.

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
