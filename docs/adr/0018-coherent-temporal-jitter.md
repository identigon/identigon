---
status: "accepted"
date: 2026-07-30
decision-makers: David Conneely
---

# 18. Coherent temporal jitter keyed on the parent's source id

## Context and Problem Statement

Related dates must move together. A contract's `start`/`end`, and a child event's date relative to
its parent's, encode orderings and intervals that tests rely on. Jittering each date independently
destroys those relationships (a schedule can land before its contract starts).

Backfilled 2026-07-30, documenting a decision made earlier in the project's development.

## Considered Options

* An independent per-field jitter delta (breaks coherence between related dates).
* A delta derived from the source primary key's `hashCode()` (trivially reversible, since the
  source PK is known - a leak).
* One shared day-delta per entity, derived from `alterego`'s salt-keyed HMAC stream, namespaced by
  a coherence group and inherited by descendants.

## Decision Outcome

Chosen option: "one shared day-delta per entity, salt-keyed and inherited by descendants", because
it is the only option that preserves parent-child date relationships without reintroducing a
reversible, hashCode-derived leak.

A child inherits its **parent's** delta, looked up by the parent's **source** id and scoped to the
group, and applies the same shift to its own dates. Each entity re-publishes its effective delta
under its own id, so a grandchild inherits the same shift through a single one-hop lookup.

### Consequences

* Good, because parent-child windows and event orderings are preserved exactly; interval
  `child - parent` is invariant under the shift.
* Good, because scoping the delta by coherence group means a table with several foreign keys
  inherits only the delta anchoring *its* group - an unrelated parent's delta can never contaminate
  it.
* Neutral: bucket-preserving `JITTER_WITHIN_MONTH` / `_YEAR` remain available for **standalone**
  dates (they preserve per-period volumes but not ordering); the shared delta is for
  ordered/related dates.
* Neutral: deltas live in the `AttributeCascadeStore`, keyed on source ids - consistent with all
  other parent lookups.
