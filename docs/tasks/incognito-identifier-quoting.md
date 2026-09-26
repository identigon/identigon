# Task: quote every SQL identifier incognito builds, not only the dialect handler's

Status: not started. Delete once implemented and green.

## 1. The gap

`PostgresDialectHandler` and `GenericDialectHandler` double-quote every table and column name they
put into SQL (`quoteIdent`), and `PostgresDialectHandlerIdentifierQuotingE2ETest` /
`PostgresDialectHandlerFkQuotingE2ETest` pin that. The stages above them concatenate raw catalog
names instead. PostgreSQL folds an unquoted identifier to lower case, so any table or column whose
catalog name is mixed-case (`"Customer"`, `"Id"`), a reserved word (`order`, `user`), or contains
anything outside `[a-z0-9_]` breaks the run. The source `SELECT` fails first, so today such a schema
cannot be cloned at all, even though the dialect layer would load it correctly.

## 2. Unquoted call sites (as of this note)

All under `incognito/src/main/java/org/identigon/incognito/`:

- `core/TableTransformLoadStage.java` - `processTable`'s source `selectSql`: the table and every
  column.
- `core/BulkDatabaseLoadStage.java` - the pass-2 deferred cyclic-FK `UPDATE`: table, FK column, PK
  column.
- `core/NonEmptyTargetGuardStage.java` - the `SELECT COUNT(*)` guard: table.
- `core/IncognitoCleanUpHandler.java` - the compensation `DELETE FROM`: table.
- `core/VerificationStage.java` - tables and (`p.`/`c.`-qualified) columns in `process` (the
  FK-integrity check), `countDistinctWithPgStatsPreFilter`, every `verify*Fictionality`,
  `queryBucketCounts`, `verifySurvival`, and the structural-uniqueness fan-out query.

`countDistinctWithPgStatsPreFilter` also builds the `pg_stats` lookup by splicing the table and
column names into **string literals** (`tablename = '...'`). A catalog name containing `'` breaks
the query; it should bind them as `PreparedStatement` parameters instead (and filter on `schemaname`
too - see `docs/tasks/incognito-schema-qualified-names.md`).

Two quieter hazards to fix in the same pass:

- `TableTransformLoadStage` reads some values back by column label (`rs.getObject(fk.getKey())`,
  `rs.getObject(c)` in `buildSourceKey`/`buildFkTransformer`). JDBC label lookup is case-insensitive
  in the PostgreSQL driver, so two columns differing only in case (`"Id"` and `id`) are ambiguous.
  Read by index - the column order is already known from `columnsToProcess`.
- The two dialect handlers each carry a private copy of `quoteIdent`. The stages need the same
  function; one shared helper (e.g. `engine.SqlIdentifiers`) avoids a third and fourth copy.

## 3. Suggested approach

1. Add the shared quoting helper (double quotes, doubling embedded `"`), and make both dialect
   handlers use it.
2. Route every site in §2 through it. Prefer building the SQL once per table (as `selectSql` already
   is) rather than per row.
3. Replace the label-based `getObject` reads with index-based ones.
4. Bind `pg_stats`' table/column as parameters.
5. Tests: see `docs/tasks/incognito-identifier-and-schema-e2e-tests.md` - a full-pipeline E2E over a
   mixed-case + reserved-word schema is the acceptance test for this task. A no-Docker unit test of
   the helper covers the embedded-quote case.

## 4. Not in scope

Schema-qualifying names (`"billing"."Customer"`) - that is
`docs/tasks/incognito-schema-qualified-names.md`. Build this task's helper so it can take an
optional schema later without changing its callers again.
