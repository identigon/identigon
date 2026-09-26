# Task: full-pipeline tests for awkward identifiers and multi-schema databases

Status: not started. Delete once implemented and green.

## 1. Why

Identifier quoting and schema handling are tested only inside the dialect handler
(`PostgresDialectHandlerIdentifierQuotingE2ETest`, `PostgresDialectHandlerFkQuotingE2ETest`). No
test runs the whole pipeline - discovery, transform, load, deferred cyclic-FK update, verification,
compensation - over a schema with mixed-case or reserved-word names, or against a database with more
than one schema. Both gaps hide real defects: `docs/tasks/incognito-identifier-quoting.md` and
`docs/tasks/incognito-schema-qualified-names.md`.

These tests are the acceptance tests for those two tasks. Land each scenario with the fix that makes
it pass, not ahead of it - the build must stay green.

## 2. Scenario A - awkward identifiers (Testcontainers PostgreSQL)

One schema exercising every statement the engine builds:

- Tables: a mixed-case name (`"Customer"`), a reserved word (`"order"`), and a name containing a
  space and an embedded double quote.
- Columns: a mixed-case identity PK (`"Id"`), a reserved-word column (`"select"`), and two columns
  differing only in case (`"Name"` and `name`) to catch label-based `ResultSet` reads.
- Relationships: a single-column FK and a composite FK between awkward tables, plus a
  self-referential FK on one of them so the pass-2 deferred `UPDATE` runs.
- Run it twice: superuser (`session_replication_role`) and a table-owner role (the
  `DISABLE TRIGGER` + FK drop/recreate path).
- Verification must actually execute on awkward names: a `DIRECT_ID` e-mail column (fictionality), a
  `distinguishing: false` column after `ANALYZE` (the `pg_stats` pre-filter), a jittered date
  (per-period volume), and `structuralUniqueness: REPORT`.
- Compensation: a variant that fails mid-load (e.g. a `CHECK` constraint the fabricated value
  violates) must leave every awkward table empty, with triggers re-enabled and sequences resynced.
- The non-empty-target guard must refuse a pre-populated awkward table, and pass an empty one.

Add a no-Docker H2 variant of the core load (H2 supports quoted identifiers and folds unquoted ones
to upper case - the mirror image of PostgreSQL), so machines and CI legs without Docker still cover
quoting.

## 3. Scenario B - multiple schemas (Testcontainers PostgreSQL)

- Source and target each hold the schema being cloned plus a **decoy** schema with same-named
  tables, their own FKs, and rows.
- Run with the target connection's `search_path` in both orders (cloned schema first, decoy first).
- Assert the decoy schema comes through untouched: row counts, and its `pg_constraint` rows (names
  and `pg_get_constraintdef`) identical before and after, in the owner-mode path too.
- Assert a table in another schema is reported rather than silently absent, and an FK into another
  schema fails closed with a message naming both schemas - until multi-schema support exists.

## 4. effigies

`discover` -> `scaffold` -> `validate` -> `run` over scenario A's schema, so a classified scaffold
runs without hand-editing names. `scaffold` already quotes names YAML would misread, and
`ScaffoldCommandTest` covers the YAML round trip; what is missing is the end-to-end run. Once
`schema:` lands, the same for scenario B.

## 5. When done

Update `docs/testing.md`'s incognito section (and "What is deliberately not covered", if
multi-schema support stays out of scope).
