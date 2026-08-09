# ADR 0012: Identifier built-ins with pinned fictional value spaces

Status: accepted (2026-07-26)

## Context

Real datasets carry checksummed or format-constrained identifiers — NHS numbers, National
Insurance numbers, driving licence numbers, passport numbers, payment card numbers. v0.1.0
offers only `pattern(...)` for these, which can reproduce the shape but neither the checksum nor
any fictionality guarantee; the deferred-list sketch was "checksum-aware (Luhn) generation" as a
pattern-language extension.

## Decision

Ship five dedicated built-ins (spec 4.8, algorithms A.5-A.9) rather than generic checksum tokens
in the pattern language:

- A fictionality guarantee is identifier-specific knowledge (which prefix is never issued, which
  field value is impossible), not something a generic `pattern("...")` token can express — a
  Luhn token would produce checksum-valid numbers indistinguishable from real cards. Built-ins
  let each identifier pin both its checksum and its fictional space.
- Each identifier needs its own frozen domain (`alterego:nhs-number`, ...) so outputs never
  correlate across identifier types — the same reason every other built-in has one.

Fictionality mechanism per identifier (full statements in spec 4.8):

| Built-in                     | Space                | Resting on                                |
|------------------------------|----------------------|--------------------------------------------|
| `nhsNumber()`                | `999` prefix         | NHS reserved test range (documented, never |
|                              |                      | issued), valid mod-11 check digit          |
| `nationalInsuranceNumber()`  | `QQ` prefix          | HMRC prefix rules: first letter `Q` never  |
|                              |                      | allocated; HMRC's own example prefix       |
| `drivingLicenceNumber()`     | surname block        | structurally impossible: a real surname    |
|                              | `99999`              | always contributes at least one letter     |
| `passportNumber()`           | `ZZ` + 7 digits      | structurally impossible for UK: real UK    |
|                              |                      | passport numbers are wholly numeric        |
| `creditCardNumber()`         | leading digit `0`    | ISO/IEC 7812 MII 0 is reserved for ISO/TC  |
|                              |                      | 68 / future assignment; no scheme issues   |
|                              |                      | from it; valid Luhn check digit            |

Scoping: the four UK-document built-ins require locale country `GB` (fail-fast otherwise);
`creditCardNumber()` is locale-independent. The driving licence output is the DVLA (Great
Britain) 16-character layout only; Northern Ireland's DVA 8-digit format is not generated.

**No `realistic()` opt-outs**, unlike phone/postcode. A realistic output here is a
credential-shaped identifier that can collide with a real person's NHS record, NI account,
licence, passport, or card — the risk-to-value ratio is categorically worse than a phone number
that might ring. Card testing against payment networks is served by the networks' published test
PANs, not by this library. This is a deliberate exclusion, revisitable per-identifier if a
concrete need appears.

Generic pattern-language extensions (`[ABC]`, `D{5}`) stay deferred; a generic checksum token is
now explicitly rejected rather than deferred, for the reason above.

## Consequences

- Five new frozen domains and five new pinned output formats join the golden-output suite; the
  A.5-A.9 algorithms are pinned by golden tests, not new vector files (they compose the
  already-vectored A.3 primitives).
- The fictional-by-default table (4.1) gains five rows across the two existing guarantee
  families: one documented reserved range (NHS), four structural impossibilities.
- `passportNumber()`'s guarantee is country-scoped (never a valid *UK* passport number), the
  same scoping as `postcode()`'s; the output's generic alphanumeric shape means passport-field
  validators that accept multiple nationalities still accept it.
- The README's extensibility example, which generates NHS numbers via a custom strategy, must
  switch to a non-built-in identifier to avoid teaching users to hand-roll what is now shipped.
- No record-coherence participation: none of the five encodes a place, and coherence between an
  identifier's embedded date-of-birth block and jittered date fields is a non-goal for this
  version.
