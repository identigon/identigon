# Task 001: schema discovery + scaffold YAML (`discover`, `scaffold`)

Status: not started (Phase 1 + first half of Phase 2 in `PLAN.md`). The first real slice: turn a
source connection into a metadata summary and a fail-closed starter `policy.yaml`.

## Goal

Implement the `discover` and `scaffold` subcommands by **reusing lib-incognito's `SchemaInspector`**
— no re-implementation, and no row reads. `discover` prints a schema summary; `scaffold` writes a
starter `policy.yaml` with every column left unclassified (so a lib-incognito run still fails
closed).

## Hard constraints (from SPECIFICATION.md §2)

- **Metadata only.** `SchemaInspector` reads JDBC `DatabaseMetaData` (tables, columns, PKs, FKs,
  unique indexes, types) — no `SELECT` of data. Do not add any row read anywhere in this path.
- **Fail-closed.** The scaffold must **not** assign roles. Emit each column with its role left to be
  filled (a commented placeholder or an explicit empty), so lib-incognito aborts until a human
  decides. Do not default a column to `PAYLOAD`.
- **No secrets in files.** The source connection URL/user come from CLI args; the password from an
  environment variable. Never echo the password; never write it into the scaffold.

## Steps

**Step 1 — a source `DataSource` from CLI input.** Add options to `discover`/`scaffold` for the JDBC
URL and user (e.g. `--source-url`, `--source-user`), and read the password from an env var (e.g.
`IDENTIGON_SOURCE_PASSWORD`). Wrap them in a minimal `javax.sql.DataSource` (a `DriverManager`-backed
record, as the lib-incognito E2E tests do). Keep a tiny option parser for now — do not pull in a CLI
framework yet.

**Step 2 — discover via `SchemaInspector`.** Call `new
org.identigon.incognito.engine.SchemaInspector().inspect(dataSource)` → `List<TableMetadata>`. Each
`TableMetadata` exposes `tableName()`, `columns()`, `primaryKeyColumns()`, `columnTypes()`
(`Map<String,Integer>` of `java.sql.Types`), `generatedColumns()`, `foreignKeys()`
(`Map<column,parentTable>`), and `foreignKeyConstraints()` (composite-aware). `discover` prints a
readable summary per table (columns with type name via `java.sql.JDBCType`, PK, FKs).

**Step 3 — emit the scaffold `policy.yaml`.** Mirror lib-incognito's YAML shape so its
`YamlPolicyParser` consumes the finished file:

```yaml
autoInfer: false
tables:
  <table>:
    columns:
      <column>:            # type: VARCHAR, pk, fk -> <parent>   (discovered metadata, as a comment)
        role:              # TODO classify — see the role vocabulary; run fails closed until filled
```

- Skip generated columns (`generatedColumns()`) — lib-incognito excludes them from classification.
- Put the discovered type/PK/FK on each column as a trailing comment to guide whoever fills `role`.
- Leave `role` empty/absent so the run fails closed (do **not** infer here — inference is Task 003).
- Write to a path given by `--out` (default `./policy.scaffold.yaml`); never overwrite silently.

**Step 4 — tests.** No live DB needed for the emitter: unit-test the YAML rendering from a
hand-built `List<TableMetadata>` (construct the record directly), asserting every non-generated
column appears with an empty `role` and the metadata comment. A Testcontainers E2E (mirroring
lib-incognito's E2E scaffolding) can cover the end-to-end `discover`/`scaffold` against a real
PostgreSQL later.

## Definition of done

- `./gradlew build` green (compile, tests, SpotBugs).
- `discover` prints a metadata-only summary; `scaffold` writes a fail-closed `policy.yaml` that
  lib-incognito's `YamlPolicyParser` parses and then rejects at run time until roles are filled.
- No row-data read anywhere; no secret written to any file.
- `PLAN.md` Phase 1 (and the scaffold half of Phase 2) ticked; `CHANGELOG.md` Unreleased updated.

## Not in this task

Role **inference** (pre-filling suggestions) is Task 003 / Phase 3 — the migration of
lib-incognito's `PolicyInferrer`. The scaffold here stays deliberately empty.
