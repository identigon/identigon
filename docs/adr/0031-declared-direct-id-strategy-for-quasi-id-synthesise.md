---
status: "proposed"
date: 2026-09-01
decision-makers: {who decided - required once the status is not "proposed"}
---

# 31. Declared `directIdStrategy` for character-type `QUASI_ID` `SYNTHESISE`, not a silent fallback

## Context and Problem Statement

Appendix B's `SYNTHESISE`-by-type table let a `QUASI_ID` column on a character type (`VARCHAR` and
friends) with no `directIdStrategy` hint fall back to shape-preserving fabrication - the same
guarantee-less mechanism ADR 21 already named for `DIRECT_ID`'s `ALTEREGO_GENERIC`. Unlike
`DIRECT_ID`, this fallback was never a declared choice: `SchemaDiscoveryStage` treated character
types as unconditionally "synthesisable" alongside temporal types, so a `postcode` column classified
`QUASI_ID` with the default `SYNTHESISE` strategy and no hint passed validation and produced
structurally-valid output that can collide with a real postcode - exactly the outcome
"guaranteed-fictional output" promises to prevent. External tutorial feedback demonstrated this on
the shipped quickstart schema: removing the `directIdStrategy: ALTEREGO_POSTCODE` hint from
`postcode` still produced a passing run with no findings, and two of three sampled output values
were structurally valid, potentially-real UK postcodes.

`effigies scaffold` compounded it: the `directIdStrategy` TODO stub it emits for an inferred
`DIRECT_ID` column has no equivalent for an inferred `QUASI_ID` column, so an author following the
scaffold on a column it identified as a postcode landed on the no-guarantee path with nothing
suggesting otherwise.

This is the same problem ADR 29 already closed for `DIRECT_ID`, one level short: the system should
not pick a guarantee-less fabrication tool silently just because the author didn't specify one.

## Considered Options

- Leave the character-type shape-preserving fallback as Appendix B specified it (status quo) -
  `effigies scaffold` gains the missing stub, but incognito keeps accepting a hint-less
  character-type `QUASI_ID` `SYNTHESISE` column.
- Require an explicit `directIdStrategy` hint on a character-type `QUASI_ID` using `SYNTHESISE`,
  fail-closed at discovery when absent - extends ADR 29's `DIRECT_ID` treatment to `QUASI_ID`,
  scoped to character types only (temporal types keep their existing, type-matched shift primitive
  with no hint required).

## Decision Outcome

Chosen option: "require the hint", because a character-type `QUASI_ID` whose `SYNTHESISE` strategy
resolves to shape-preserving fabrication is exactly the unmade decision ADR 29 already ruled out for
`DIRECT_ID` - the role name changes what the value is used for, not whether silently picking a
guarantee-less fallback is acceptable.

`SchemaDiscoveryStage.validateSynthesiseType` now distinguishes temporal types (a type-matched shift
primitive - `shiftDate`/`shiftDateTime` - needs no hint) from character types (shape-preserve only,
so a hint is always required) from every other type (already required a hint before this decision).
`ALTEREGO_GENERIC` remains fully available as an explicit hint, exactly as ADR 29 kept it for
`DIRECT_ID` - this does not narrow what a character-type `QUASI_ID` can be fabricated with, only
requires the choice be stated. `effigies scaffold` gains a `QUASI_ID` stub for heuristics with a
known typed generator (`POSTCODE_PATTERN` today), mirroring the existing `DIRECT_ID` stub.

### Consequences

- Good, because it closes the gap that let a `QUASI_ID` postcode column report success while
  producing a structurally-valid, potentially-real postcode - the DPIA report's fictionality
  labelling and a `QUASI_ID` column's actual guarantee level can no longer diverge silently.
- Good, because it brings `QUASI_ID` in line with the `DIRECT_ID` treatment ADR 29 already
  established, rather than leaving the two roles inconsistent.
- Bad, because it is a breaking change: any existing policy with a character-type `QUASI_ID`
  `SYNTHESISE` column and no `directIdStrategy` hint now fails closed until a hint - even
  `ALTEREGO_GENERIC` - is added. This repo's own benchmark fixtures (chinook, northwind) and the
  SPEC's own Appendix B example needed exactly this fix.
- Neutral: temporal `SYNTHESISE` columns (`DATE`/`TIMESTAMP`) are unaffected - the type-matched
  shift primitive remains the default, no hint required.
- Neutral: does not touch `VerificationStage`'s fictionality checks or the source-value survival
  net - an explicitly-chosen `ALTEREGO_GENERIC` hint is exactly as unguaranteed as before, and
  exactly as ADR 21 already accepted for `DIRECT_ID`.
