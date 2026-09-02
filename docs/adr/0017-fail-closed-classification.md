---
status: "accepted"
date: 2026-07-30
decision-makers: David Conneely
---

# 17. Fail-closed classification

## Context and Problem Statement

The one mistake that leaks real data is an identifier nobody classified - a column the policy
simply did not mention, copied through verbatim because the engine assumed it was harmless.
Auto-inference can _guess_ a column's role from its name, but a wrong or missing guess must never
result in real data being copied silently.

Backfilled 2026-07-30, documenting a decision made earlier in the project's development.

## Considered Options

- Fail-open: an unclassified column defaults to a safe-seeming pass-through role.
- Fail-open with inference: auto-inference assigns a role when confident enough.
- Fail-closed: any column with no explicit or accepted-inferred role aborts the run.

## Decision Outcome

Chosen option: "fail-closed", because both fail-open variants risk an unspotted identifier being
copied through as real data - exactly the failure mode this project exists to prevent.

Every discovered column must resolve to a `ColumnRole` before the run starts. A column with no
explicit role - and no _accepted_ inferred role - **aborts the run** with `ConfigException`, even
when auto-inference is enabled. Auto-inference only adds _suggestions_ to the report; it never
assigns a role. A `SENSITIVE` column with no `distinguishing` declaration (ADR 16) fails the same
way. Opaque/untransformable types that are retained are surfaced in the report, never silently
copied.

### Consequences

- Good, because it is safe by default: an unspotted identifier stops the pipeline instead of
  leaking.
- Bad, because the author is forced to make an explicit decision per column; the cost is that a
  brand-new column in the source will fail a previously-passing policy until it is classified -
  which is the point.
- Neutral: validation happens at config/discovery time, before any row is read, so failures are
  cheap and early.
