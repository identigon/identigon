---
status: "accepted"
date: 2026-07-14
decision-makers: David Conneely
---

# 25. London listed once per postcode area in the towns dictionary, not as one row

## Context and Problem Statement

The `city()`/coherence towns dictionary lists one postcode area per entry (spec section 6.3,
`UK_POSTCODE_AREA`). London spans eight postcode areas (E, EC, N, NW, SE, SW, W, WC), so it does not
fit the dictionary's one-area-per-entry model as a single row - but London is the UK's largest city
and cannot be dropped from a population-ranked list.

## Considered Options

- Pick one postcode area to represent London (e.g. `EC` for the City) and treat the rest as
  uncovered.
- Exclude London from the dictionary entirely, since no single area represents it.
- List London once per major postcode area it spans (eight rows, all tagged `ENGLAND`).

## Decision Outcome

Chosen option: "list London once per major postcode area it spans", because picking one area would
be an inaccurate representation of London's actual postcode geography, and excluding the UK's
largest city entirely is worse than either.

The 20-town dictionary carries 27 rows once London's eight areas are counted. `phoneNumber()`'s
range-to-area coherence tagging (`docs/research/0003-alterego-phone-ranges.md`) follows the same
precedent for its own single London range.

### Consequences

- Good, because `city()`/`postcode()`/`phoneNumber()` record coherence (ADR 8) can resolve to any of
  London's real postcode areas, not just one arbitrarily chosen one.
- Neutral: the towns dictionary has 27 rows for 20 distinct places, not a 1:1 count - documented so
  it isn't mistaken for a duplication bug.
- Neutral: this is not a duplicate-row well-formedness violation, since the tag (postcode area)
  differs per row - the same "list once per area" rule `phoneNumber()`'s own range table later
  reused for its single London range.

<!-- Extracted from alterego/docs/dictionaries.md's "Towns/cities" section ("Judgement call,
     decided") during the docs/research/ migration; the full sourcing and curation detail for the
     towns dictionary stays in docs/research/0001-alterego-dictionaries.md. -->
