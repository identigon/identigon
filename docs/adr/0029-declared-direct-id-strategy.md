---
status: "accepted"
date: 2026-08-31
decision-makers: David Conneely
---

# 29. Declared `directIdStrategy` for `DIRECT_ID`, not an automatic default

## Context and Problem Statement

`DIRECT_ID` is the one role whose whole purpose is the fictionality guarantee (§4.3): a name, email,
or NINO fabricated so it cannot be mistaken for real data. A `DIRECT_ID` column with no
`directIdStrategy` has always silently resolved to `ALTEREGO_GENERIC` - shape-preserving character
substitution, which ADR 21 already settled carries **no** such guarantee. A policy author who writes
`role: DIRECT_ID` and nothing else gets that weaker fabrication without ever being told, and the
DPIA report has no distinct signal for it - `VerificationStage`'s per-strategy fictionality checks
(email/postcode/domain/URL/NINO/NHS/passport/driving-licence) don't apply to `ALTEREGO_GENERIC`, so
the only net under it is the source-value survival check, which ADR 21 itself already calls "a
probabilistic net, not a guarantee."

This is not a reason to revisit ADR 21: `ALTEREGO_GENERIC` existing, and being guarantee-less, is
still correct - some `DIRECT_ID` columns (a bank account number, say) have no typed generator to
route to, and shape-preserving fabrication is the right tool for them. The question here is
narrower: should the system pick that tool _silently_, or should the author _say so_.

`SENSITIVE` already answers the equivalent question for its own decision (`distinguishing`, ADR 16):
declared, validated at config time, never inferred from data or defaulted silently. `DIRECT_ID`'s
strategy choice was never brought into line with that.

## Considered Options

- Leave `directIdStrategy` optional, defaulting to `ALTEREGO_GENERIC` (status quo).
- Require `directIdStrategy` on every `DIRECT_ID` column, validated at config time alongside the
  existing `SENSITIVE`/`distinguishing` check - `IncognitoException.ConfigException`, fail-closed,
  before any row is read.

## Decision Outcome

Chosen option: "require `directIdStrategy`", because a `DIRECT_ID` with no strategy is an unmade
decision, not a default - the same reasoning ADR 16 already applied to `distinguishing`, applied
consistently to the other role whose transformation is a policy-author choice rather than a fixed
mechanism.

`SchemaDiscoveryStage.validateTablePolicy` gains a `DIRECT_ID` branch alongside its existing
`SENSITIVE` one: a `DIRECT_ID` column with `directIdStrategy() == null` fails the run before any row
is read. `ALTEREGO_GENERIC` remains fully available as an explicit choice - this does not narrow
what a `DIRECT_ID` column can be fabricated with, only requires the choice be stated.
`UNIQUE_CANDIDATE_KEY` is unaffected: it shares `buildDirectIdTransformer`'s code path but was never
claimed to carry a fictionality guarantee in the first place (§4.1's own row for it says only
"fabricate via `AlterEgo.unique()`"), so its silent `ALTEREGO_GENERIC` default stays as-is.

### Consequences

- Good, because every existing policy in the repo (all four benchmark fixtures, the quickstart
  example, every test) already declares `directIdStrategy` explicitly, including the ones that
  choose `ALTEREGO_GENERIC` - this closes the one path none of them take, rather than breaking a
  path any of them relied on.
- Good, because the DPIA report's fictionality-verified label and a `DIRECT_ID` column's actual
  guarantee level can no longer diverge silently: `ALTEREGO_GENERIC` still carries none, but now
  only by an author's explicit, visible choice, not by omission.
- Neutral: does not touch `VerificationStage`'s checks or the source-value survival net - an
  explicitly-chosen `ALTEREGO_GENERIC` column is exactly as unguaranteed as it was before, and
  exactly as ADR 21 already accepted.
