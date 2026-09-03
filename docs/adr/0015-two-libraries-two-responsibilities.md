---
status: "accepted"
date: 2026-07-30
decision-makers: David Conneely
---

# 15. Two libraries - value transformation vs relational coherence

## Context and Problem Statement

Incognito is a distinct library from `alterego`. The original justification ("Alterego works on
records, Incognito works on datasets") was a k-anonymity-era assumption - that Incognito needed a
global statistical view. With fabrication (ADR 14) there is no such pass, so that framing no longer
holds and the real reason for the split had to be restated.

Backfilled 2026-07-30, documenting a decision made earlier in the project's development. Refined by
ADR 21.

## Considered Options

- One combined library covering both single-value fabrication and whole-database relational
  coherence.
- Two libraries, split by responsibility rather than by batch size.

## Decision Outcome

Chosen option: "two libraries, split by responsibility", because folding relational concerns into
the value-transformation library would couple it to JDBC and destroy its reuse as a standalone value
transformer.

Split by **responsibility**, not batch size:

- **`alterego`** transforms a single value or the fields of one record - deterministic in
  `(salt, domain, value)`, stateless with respect to the dataset, and DB-agnostic (reusable on a
  CSV, an API payload, or a message).
- **Incognito** owns everything relational: schema discovery and role classification, topological
  load order, key translation (PK -> surrogate, FK rewritten to the same mapping), coherent
  cross-entity temporal deltas, root-ancestor attribute cascade, bulk loading with trigger
  isolation, and DPIA reporting.

Incognito **consumes** Alterego and delegates **all** field-value substitution to it. Where
Incognito needs a value transformation Alterego does not expose, the fix is to add the primitive to
Alterego - never to hand-roll it in Incognito.

### Consequences

- Good, because Alterego stays reusable as a standalone value transformer; folding relational
  concerns in would couple it to JDBC and destroy that.
- Neutral: the boundary is a one-line test: _Alterego fabricates fields; Incognito preserves
  relationships._
- Bad, because any value logic that leaks into Incognito must be tracked as debt, not sanctioned by
  default - see ADR 21 for the one case where that characterisation was later revisited and
  reversed.
