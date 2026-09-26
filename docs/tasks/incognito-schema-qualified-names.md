# Task: make incognito schema-aware instead of relying on `search_path`

Status: not started. Delete once implemented and green.

## 1. The gap

incognito identifies a table by its bare name everywhere, and resolves that name through each
connection's `search_path`:

- `SchemaInspector.inspect` discovers only `connection.getSchema()` - the source connection's
  current schema - and ignores `TABLE_SCHEM`/`PKTABLE_SCHEM`. `TableMetadata`, the policy's
  `tables:` map, the key-translation store and the dependency graph are all keyed by bare name.
- Every SQL statement the engine builds names tables unqualified (see
  `docs/tasks/incognito-identifier-quoting.md` for the list), so the target connection's
  `search_path` silently decides which table is loaded, guarded, cleaned up or verified. Nothing
  checks that it points at the same schema the source was discovered from.
- `PostgresDialectHandler.dropForeignKeysReferencing` reads `pg_constraint` across **every**
  namespace, matches parent tables by bare `relname`, then runs
  `ALTER TABLE <child> DROP CONSTRAINT` unqualified. In a target with two schemas that both hold a
  table of that name, it captures constraints belonging to the other schema and then drops or
  recreates them against whichever table `search_path` resolves - failing the run at best.
- `VerificationStage.countDistinctWithPgStatsPreFilter` reads `pg_stats` without a `schemaname`
  filter, so a same-named table's statistics elsewhere can feed the distinguishing lint. The lint is
  a safety net, not the privacy gate, but a wrong low estimate skips the exact count.

Consequences for a multi-schema database: tables outside the current schema are never discovered, so
the clone is not schema-identical and nothing in the `AnonymisationReport` says so. An FK into
another schema fails the run (no key translation, or "not found in the discovered schema").

## 2. Suggested scope, in two steps

1. **One explicit schema, done properly.** A `schema:` policy key (defaulting to today's behaviour:
   the source connection's current schema), used to filter every catalog query
   (`getTables`/`getColumns`/`getImportedKeys`/`getIndexInfo`, `pg_constraint` via
   `connamespace`/`relnamespace`, `pg_stats.schemaname`) and to schema-qualify every statement on
   both connections. Resolve the target's schema explicitly too, rather than trusting its
   `search_path`. Fail closed if the target lacks that schema.
2. **Multiple schemas** (later, if a real need appears). Table identity becomes `(schema, name)`
   throughout `TableMetadata`, the dependency graph and the stores; the policy keys tables as
   `schema.table`; cross-schema FKs resolve normally. This changes the policy format and effigies'
   `scaffold` output, so it needs an ADR.

Step 1 changes the policy schema (a new root key), so update `docs/spec/incognito.md` §6, effigies'
`scaffold` (emit the discovered schema) and the `identigon-policy-author` skill with it.

## 3. Tests

See `docs/tasks/incognito-identifier-and-schema-e2e-tests.md`: a target with the cloned schema plus
a decoy schema holding same-named tables (with FKs of their own) is the acceptance test - the decoy
must come through the run untouched.

## 4. Build on

`docs/tasks/incognito-identifier-quoting.md`'s shared quoting helper - give it an optional schema so
qualifying names is one change at each call site, not two.
