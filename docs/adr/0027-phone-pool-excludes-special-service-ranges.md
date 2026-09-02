---
status: "accepted"
date: 2026-07-25
decision-makers: David Conneely
---

# 27. phoneNumber()'s default pool excludes freephone/premium-rate/UK-wide ranges

## Context and Problem Statement

Ofcom publishes 20 reserved drama/test phone ranges: 14 geographic, one generic no-area fallback,
one mobile, and three special-service ranges (freephone, premium-rate, UK-wide non-geographic).
`phoneNumber()` aims for realistic output (specification section 1) - an ordinary person's phone
field is essentially never a freephone, premium-rate, or UK-wide non-geographic number.

## Considered Options

- Ship all 20 published Ofcom ranges in the default pool.
- Ship only the 17 ranges that read as a realistic personal contact number (geographic + no-area +
  mobile), keeping the other 3 documented but unshipped.

## Decision Outcome

Chosen option: "ship only the 17 realistic-personal-number ranges", because the three excluded
ranges, while genuine reserved Ofcom ranges, don't read as a plausible personal phone number and
would be an obvious artefact in fabricated data - consistent with the specification's own section
4.1 illustrative examples (`020 7946 0xxx`, `07700 900xxx`, `01632 960xxx`), all
geographic/mobile/generic, never a special-service range.

The three excluded ranges (freephone, premium-rate, UK-wide) are recorded in full in
`docs/research/0003-alterego-phone-ranges.md` so a future version can add them behind an option
without re-doing the sourcing work.

### Consequences

- Good, because default output never surprises a reviewer with an implausible number type for a
  personal contact field.
- Neutral: the unconstrained ("no area fixed") pool is exactly 17 distinct choices, not 20 - a
  future opt-in for the excluded three is additive, not a breaking change to today's pool.
- Bad, because a caller who specifically wants a fictional freephone/premium-rate/UK-wide number
  has no built-in option for it yet - deferred, not solved.

<!-- Extracted from alterego/docs/phone-ranges.md's "v1 scope" section during the docs/research/
     migration; the full Ofcom sourcing and licensing detail stays in
     docs/research/0003-alterego-phone-ranges.md. -->
