---
status: "accepted"
date: 2026-07-30
decision-makers: David Conneely
---

# 19. Cyclic foreign keys via Tarjan SCC + placeholder + 2-pass UPDATE

## Context and Problem Statement

A schema-identical clone must load tables whose foreign keys form a cycle, including the common
self-referential case (`employee.manager_id -> employee`). Strict topological ordering - parents
before children - cannot order a cycle: some row always references a row not yet loaded. Silently
dropping such tables (an earlier behaviour) is a data-integrity hole.

Backfilled 2026-07-30, documenting a decision made earlier in the project's development.

## Considered Options

* Silently drop tables involved in a foreign-key cycle.
* Detect strongly-connected components (Tarjan's SCC), insert a type-appropriate placeholder for
  an unresolved FK, and resolve it in a second pass once every table is loaded.

## Decision Outcome

Chosen option: "Tarjan SCC plus a placeholder and a second-pass UPDATE", because it is the only
option that loads a cyclic schema with referential integrity intact, rather than silently losing
data.

Detect strongly-connected components with **Tarjan's SCC**, condense them, and topologically order
the condensation (parents before children; a cycle becomes one component). For a foreign key that
cannot yet be resolved during a component's load, insert a **type-appropriate placeholder** (Pass
1) with FK enforcement suppressed on the insert connection, record a deferred update, and after all
tables are loaded run a second-pass **`UPDATE`** setting the real mapped surrogate (Pass 2). It is
**fail-closed**: a deferred FK on a row with no resolvable single-column primary key throws rather
than leaving a dangling placeholder.

### Consequences

* Good, because cyclic and self-referential schemas load with referential integrity intact after
  Pass 2.
* Neutral: Pass 1 relies on suppressing FK enforcement - `session_replication_role = 'replica'`
  (superuser), or a documented degraded owner-mode; a non-superuser without FK-dropping fails loud
  on the placeholder insert.
* Bad, because composite primary/foreign keys were not supported at the time of this decision
  (Pass 2 keyed on a single-column PK only); until then a cyclic table without a single-column PK
  fails-closed rather than corrupting data. (Composite PK support landed separately; the
  composite-PK-and-cyclic-FK combination remains open - see root `PLAN.md`.)
* Neutral: verified end-to-end by a mutual self-reference test (`CyclicFkE2ETest`).
