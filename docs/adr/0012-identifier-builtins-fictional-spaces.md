---
status: "accepted"
date: 2026-07-26
decision-makers: David Conneely
---

# 12. Identifier built-ins with pinned fictional value spaces

## Context and Problem Statement

Real datasets carry checksummed or format-constrained identifiers - NHS numbers, National Insurance
numbers, driving licence numbers, passport numbers, payment card numbers. v0.1.0 offers only
`pattern(...)` for these, which can reproduce the shape but neither the checksum nor any
fictionality guarantee; the deferred-list sketch was "checksum-aware (Luhn) generation" as a
pattern-language extension.

## Considered Options

- A generic checksum token (e.g. Luhn-aware) added to the `pattern(...)` mini-language.
- Five dedicated, identifier-specific built-ins, each with its own pinned fictional value space.

## Decision Outcome

Chosen option: "five dedicated, identifier-specific built-ins", because a fictionality guarantee is
identifier-specific knowledge (which prefix is never issued, which field value is impossible) that a
generic pattern token cannot express - a Luhn token would produce checksum-valid numbers
indistinguishable from real cards.

Ship five dedicated built-ins (spec 4.8, algorithms A.5-A.9) rather than generic checksum tokens in
the pattern language. Each identifier needs its own frozen domain (`alterego:nhs-number`, ...) so
outputs never correlate across identifier types - the same reason every other built-in has one.

Fictionality mechanism per identifier (full statements in spec 4.8):

| Built-in                    | Space                 | Resting on                                                                                                         |
| --------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `nhsNumber()`               | `999` prefix          | NHS reserved test range (documented, never issued), valid mod-11 check digit                                       |
| `nationalInsuranceNumber()` | `QQ` prefix           | HMRC prefix rules: first letter `Q` never allocated; HMRC's own example prefix                                     |
| `drivingLicenceNumber()`    | surname block `99999` | structurally impossible: a real surname always contributes at least one letter                                     |
| `passportNumber()`          | `ZZ` + 7 digits       | structurally impossible for UK: real UK passport numbers are wholly numeric                                        |
| `creditCardNumber()`        | leading digit `0`     | ISO/IEC 7812 MII 0 is reserved for ISO/TC 68 / future assignment; no scheme issues from it; valid Luhn check digit |

Scoping: the four UK-document built-ins require locale country `GB` (fail-fast otherwise);
`creditCardNumber()` is locale-independent. The driving licence output is the DVLA (Great Britain)
16-character layout only; Northern Ireland's DVA 8-digit format is not generated.

**No `realistic()` opt-outs**, unlike phone/postcode. A realistic output here is a credential-shaped
identifier that can collide with a real person's NHS record, NI account, licence, passport, or
card - the risk-to-value ratio is categorically worse than a phone number that might ring. Card
testing against payment networks is served by the networks' published test PANs, not by this
library. This is a deliberate exclusion, revisitable per-identifier if a concrete need appears.
Generic pattern-language extensions (`[ABC]`, `D{5}`) stay deferred; a generic checksum token is
rejected outright, for the reason above.

### Consequences

- Good, because five new frozen domains and five new pinned output formats join the golden-output
  suite; the A.5-A.9 algorithms are pinned by golden tests, not new vector files (they compose the
  already-vectored A.3 primitives).
- Good, because the fictional-by-default table gains five rows across the two existing guarantee
  families: one documented reserved range (NHS), four structural impossibilities.
- Neutral: `passportNumber()`'s guarantee is country-scoped (never a valid _UK_ passport number),
  the same scoping as `postcode()`'s; the output's generic alphanumeric shape means passport-field
  validators that accept multiple nationalities still accept it.
- Neutral: the README's extensibility example, which generates NHS numbers via a custom strategy,
  must switch to a non-built-in identifier to avoid teaching users to hand-roll what is now shipped.
- Bad, because none of the five participates in record coherence: none encodes a place, and
  coherence between an identifier's embedded date-of-birth block and jittered date fields is a
  non-goal for this version.
