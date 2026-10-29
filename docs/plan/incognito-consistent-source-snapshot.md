# Task: read the whole source from one consistent snapshot

Status: not started. Delete once implemented and green.

## 1. The gap

`docs/spec/incognito.md` §1.3 assumes the source is an **offline snapshot** or staging copy, but
nothing enforces or provides that. Every source read opens its own connection at the driver's
default isolation (READ COMMITTED on PostgreSQL), so each table - and each verification query - sees
whatever was committed when _it_ started:

- `TableTransformLoadStage.processTable` opens a fresh source connection per table.
- `VerificationStage` opens four more (the distinguishing lint, per-period volume, source-value
  survival, structural uniqueness), after the whole load has finished.

Pointed at a live database, that goes wrong in two ways:

- **The run aborts.** A child row committed after its parent table was read carries an FK value with
  no key translation. `buildFkTransformer` fails closed
  (`No key translation for a value of FOREIGN_KEY column ...`), and the compensation handler then
  empties the target. Nothing is corrupted, but a busy source may never clone successfully.
- **Verification compares different moments.** Volume and survival checks read the source again
  after the load, so rows written in between show up as spurious drift - or mask real drift.

## 2. Options

1. **One long-lived source connection** in `REPEATABLE READ`, read-only, opened once and shared by
   every stage through `PipelineContext`. On PostgreSQL the snapshot is taken at the first query and
   holds for the transaction. Simplest; fits the single-threaded pipeline (parallel execution is a
   §1.2 non-goal). Needs every stage to stop calling `context.source().getConnection()` for reads.
2. **Exported snapshot**: a coordinating connection runs `pg_export_snapshot()` inside a
   `REPEATABLE READ` transaction and stays open; every other source connection starts its own
   `REPEATABLE READ` transaction with `SET TRANSACTION SNAPSHOT '<id>'`. Keeps per-stage connections
   and would survive a future move to parallel table loads, but is PostgreSQL-only and belongs
   behind `DialectHandler`.
3. **Keep the assumption, make it louder**: document in the spec, README and effigies' `run` help
   that the source must be quiescent, and do nothing in code. Cheapest; leaves the failure mode in
   place.

Option 1 or 2 is a real choice with trade-offs (portability versus future parallelism) - record it
in an ADR. Either way, update spec §1.3 to say what the engine now guarantees.

## 3. Caveats to handle or document

- A long snapshot on a **primary** holds back vacuum's `xmin` horizon for the whole run - table and
  index bloat on a large, busy database. Recommend a physical replica.
- On a **hot standby**, a long query can be cancelled by recovery conflicts unless
  `hot_standby_feedback` is on (which moves the bloat to the primary) or
  `max_standby_streaming_delay` is raised.
- `GenericDialectHandler`: `TRANSACTION_REPEATABLE_READ` means different things per engine (some
  lock rather than snapshot). Either leave the generic path at today's behaviour or make the
  isolation level a dialect decision.
- Schema discovery reads catalog metadata, which is not snapshot-isolated the same way. A DDL change
  mid-run should still fail the run, not be papered over.

## 4. Tests

A Testcontainers E2E that commits new parent + child rows on a second connection between two table
loads (a hook in the stage, or a trigger-driven writer), asserting the run succeeds and the clone
matches the pre-write snapshot. Without the fix it fails with the `No key translation` error, which
makes it a clean acceptance test.
