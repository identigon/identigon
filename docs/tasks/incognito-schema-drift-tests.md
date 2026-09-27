# Task: prove fail-closed holds when the schema drifts after a policy is approved

Status: not started. Delete once implemented and green.

## 1. The gap

Fail-closed classification (ADR 17, spec §7.2) is the property the whole privacy argument rests on.
An external evaluation called it the project's strongest design choice, and said it should be tested
the hardest. The property that matters is not that `validate` reports drift, but that **no execution
path lets an unclassified or wrongly-typed source value reach the target unnoticed**.

Today's tests cover a column missing from the policy against a static schema. Nothing takes a
schema, approves a policy for it, and then changes the schema the way a migration would before the
next `validate`/`run`.

## 2. Scenarios

Each one starts from a policy that passes `validate` against the original schema, applies one change
to the **source** (and, where it matters, the target), then runs both `validate` and `run`.

| Change after approval                                                      | Expected                                                                                                                |
| -------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| New column holding PII (e.g. `customers.mobile`)                           | both fail, naming the column; no rows written                                                                           |
| Column renamed (`email` -> `email_address`)                                | both fail: new column unclassified, and the stale policy entry reported - pin whichever the spec says for the stale one |
| Table added                                                                | both fail (unclassified table)                                                                                          |
| Table renamed or dropped                                                   | fails, or is reported - pin the spec'd behaviour; never a silent partial clone                                          |
| Type change on a `QUASI_ID` + `SYNTHESISE` column (`DATE` -> `VARCHAR`)    | fails closed: character type with no hint (Appendix B, ADR 31), not shape-fabricated                                    |
| Type change on a `PAYLOAD` column to hold something new (`INT` -> `TEXT`)  | kept real by design - document it as residual risk that only a human review can catch                                   |
| New FK added on a column classified `PAYLOAD`                              | must not copy real parent keys unnoticed - decide and pin (fail, or report)                                             |
| FK removed from a column classified `FOREIGN_KEY`                          | pin: fails, or translates via the declared `references`                                                                 |
| New unique constraint on a fabricated column                               | `run` succeeds with unique fabricated values, or fails clearly - never a constraint violation mid-load                  |
| New `NOT NULL` on a column classified `redactionStrategy: CLEAR`           | fails before the load, not partway through it                                                                           |
| Generated column added / column given a default                            | generated: excluded; default-only: still needs a role                                                                   |
| Database-specific type added (`jsonb`, `text[]`, `inet`, `geometry`)       | fails closed (these map to reserved roles, spec §4.1)                                                                   |
| Target schema differs from source (column missing or extra in target only) | `run` fails before loading                                                                                              |

For every scenario that is expected to succeed, the source-value survival check must confirm that no
source value of a fabricated column appears in the target.

## 3. Notes

- Write the scenarios as Testcontainers integration tests in `incognito` (the engine is what must
  hold the line), plus a thin `effigies validate` test for the CLI exit code and the message naming
  the column.
- Where a row reads "pin", the spec may not say yet. Flag the gap (AGENTS.md: do not invent
  behaviour) and settle it in `docs/spec/incognito.md` before writing that test.
- Any scenario that currently lets a value through is a `bug` entry in `PLAN.md` of its own, ranked
  at the top - not a test to mark as skipped.
