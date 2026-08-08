# Task: support composite PK + cyclic FK together

Status: not started (build-time handoff; delete this file once implemented and green)

## 1. What's missing

A table that has **both** a composite (multi-column) primary key **and** participates in a cyclic /
self-referential foreign key is not supported. The 2-pass cyclic-FK loader resolves each deferred FK
with an `UPDATE … WHERE <pk> = ?` keyed on a **single** PK column, so a composite PK cannot be
targeted. Today this **fails closed** with a clear message (SPEC §5.2) — it never corrupts data —
but the feature itself is absent. It is not exercised by any benchmark.

The fail-closed guard is in `TableTransformLoadStage`, at the deferral site:

```java
if (targetPk == null || compositePk) {
    throw new IncognitoException.ConstraintException(
        "Cyclic FK on table '" + tableName + "' cannot be deferred: "
        + (compositePk ? "composite PK + cyclic FK is not yet supported"
                       : "the row has no resolved single-column primary key")
        + " to UPDATE in pass 2 (SPEC §5.2).");
}
```

The goal is to lift the `compositePk` half of that guard by teaching the deferred `UPDATE` to key on
**all** PK columns.

## 2. Why it is tractable

Everything the multi-column `UPDATE` needs is already computed at the deferral site — the fix is
plumbing, not new algorithm:

- `pkCols` (`List<String>` = `tableMeta.primaryKeyColumns()`) — the ordered PK column names.
- `newPkVals` (`Object[]`) — the fabricated PK values for this row, aligned to `pkCols` (populated
  for composite PKs; `allNonNull(newPkVals)` is already checked at line ~294).
- For a single-column PK, the existing `pkColumn` (`String`) and `targetPk` (`Object`) still apply.

## 3. Steps, in order

**Step 1 — widen the `DeferredUpdate` record.** In `BulkDatabaseLoadStage`, change the record from a
single PK column/value to lists so it can carry a composite key (a single-column PK is just a
one-element list):

```java
// before: (String tableName, String pkColumn, Object pkValue, String fkColumn, String referencedTable, Object sourceFkValue)
public record DeferredUpdate(
    String tableName,
    java.util.List<String> pkColumns,   // ordered PK column names
    java.util.List<Object> pkValues,    // ordered PK values, aligned to pkColumns
    String fkColumn,
    String referencedTable,
    Object sourceFkValue) {}
```

Update the record's Javadoc `@param`s (the doclint gate requires them). The compiler now flags the
two call sites: `resolveDeferredCyclicFKs` (Step 2) and the deferral site in
`TableTransformLoadStage` (Step 3).

**Step 2 — build a multi-column `WHERE` in `resolveDeferredCyclicFKs`** (`BulkDatabaseLoadStage`).
Replace the single-column `UPDATE`:

```java
String whereClause = String.join(" AND ",
    update.pkColumns().stream().map(c -> c + " = ?").toList());
String updateSql = "UPDATE " + update.tableName() + " SET " + update.fkColumn()
    + " = ? WHERE " + whereClause;
try (PreparedStatement stmt = targetConn.prepareStatement(updateSql)) {
    stmt.setObject(1, mapped.get());                 // the resolved FK surrogate
    for (int i = 0; i < update.pkValues().size(); i++) {
        stmt.setObject(2 + i, update.pkValues().get(i));   // each PK column value
    }
    stmt.executeUpdate();
}
```

The rest of `resolveDeferredCyclicFKs` (the `keyStore().get(...)` lookup and its
`ConstraintException` on a missing translation) is unchanged.

**Step 3 — populate the composite key at the deferral site** (`TableTransformLoadStage`, the `if
(!rowDeferred.isEmpty())` block). Drop `compositePk` from the guard — keep only the "no resolved PK"
case — and pass the full PK to each `DeferredUpdate`:

```java
if (targetPk == null) {   // was: targetPk == null || compositePk
    throw new IncognitoException.ConstraintException(
        "Cyclic FK on table '" + tableName
        + "' cannot be deferred: the row has no resolved primary key to UPDATE in pass 2 (SPEC §5.2).");
}
java.util.List<String> updPkCols = compositePk ? pkCols : java.util.List.of(pkColumn);
java.util.List<Object> updPkVals = compositePk
    ? java.util.Arrays.asList(newPkVals)       // aligned to pkCols; already all-non-null here
    : java.util.List.of(targetPk);
for (PendingUpdate pu : rowDeferred) {
    deferredUpdates.add(new BulkDatabaseLoadStage.DeferredUpdate(
        tableName, updPkCols, updPkVals, pu.colName, pu.refTable, pu.sourceFk));
}
```

Note `newPkVals` is guaranteed all-non-null in this branch (the composite `CompositeKey` translation
a few lines above only records when `allNonNull(newPkVals)`), so a composite `targetPk != null`
implies usable `newPkVals`. The **partial-composite-FK** guard elsewhere in `buildFkTransformer`
(`"composite + cyclic FKs are not yet supported"` when a *composite FK* references a cyclic table)
is a **different** case — leave it as-is; this task only covers a composite **PK** on a table with a
(single-column) cyclic FK.

**Step 4 — E2E test.** Add `CompositePkCyclicFkE2ETest` (Testcontainers, mirror an existing E2E's
scaffolding — `CyclicFkE2ETest` is the closest). Schema: a table with a **2-column PK** and a
**self-referential single-column FK** to its own surrogate, e.g.

```sql
CREATE TABLE node (
    tenant_id  INT  NOT NULL,
    node_id    BIGINT GENERATED ALWAYS AS IDENTITY,
    parent_id  BIGINT REFERENCES node(node_id),   -- self-ref cycle
    PRIMARY KEY (tenant_id, node_id)
);
```

Seed a small tree (a root with `parent_id` NULL, a few children pointing at the root's `node_id`),
classify `tenant_id`/`node_id` as `PRIMARY_KEY` and `parent_id` as `FOREIGN_KEY` to `node`, run the
pipeline, and assert: the run succeeds; row count preserved; every non-null `parent_id` resolves to
a real `node_id` in the target (no dangling/placeholder). This test **fails before Step 3** (hits
the old `compositePk` guard) and passes after — a good revert-check.

## 4. Definition of done

- `./gradlew build` green (compile, all tests, the strict `Xdoclint:all` javadoc gate — the widened
  `DeferredUpdate` record needs full `@param`s).
- The new E2E test passes; existing `CyclicFkE2ETest` and `CompositeKeyE2ETest` still pass (this
  change must not regress the single-PK cyclic path or the composite-PK non-cyclic path).
- Update `PLAN.md` Phase 3: tick the "composite PK + cyclic FK" sub-item and note the covering test.
- If `SPECIFICATION.md` §5.2 states this combination is unsupported, update that sentence to reflect
  that it is now handled.
