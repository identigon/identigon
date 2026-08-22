# Task: support composite PK + cyclic FK together

Status: not started; **larger than first scoped** — see §2. A build-time handoff; delete once
implemented and green.

## 1. What's missing

A table whose primary key is composite (multi-column) cannot participate in a cyclic /
self-referential foreign-key group. Today this **fails closed** (SPEC §5.2) — it never corrupts
data, and no benchmark hits it — but the feature is absent.

## 2. Why this is bigger than "widen the deferred UPDATE" (correction)

An earlier draft of this task assumed the only fix was to make the pass-2 cyclic-FK `UPDATE` key on
all PK columns instead of one (the guard in `TableTransformLoadStage` that rejects `targetPk == null
|| compositePk`). **That is necessary but not sufficient, and on its own is unreachable.** The
reason is structural:

- A single-column FK is only *deferred* when its target row is **not yet mapped** at apply time — a
  genuine forward reference, which only happens **inside a cycle** (`buildFkTransformer`,
  single-column branch: `keyStore.get(...)` misses, then `cyclicTables.contains(...)` →
  `CyclicFkException`).
- For a **composite-PK** table's FK to defer, that table must therefore be *in* the cycle — which
  means something references it. Because its PK is composite, that back-reference is a **composite
  FK**.
- When that composite FK's parent isn't yet mapped (exactly the forward-reference that defines a
  cycle), it hits a **different, earlier guard** — `buildFkTransformer`'s composite branch:
  `"composite + cyclic FKs are not yet supported"`. That fires before the composite-PK row guard is
  ever reached.

So the real, blocking gap is **deferred resolution of a composite FK that references a cyclic
table**, and the composite-PK `UPDATE`-keying is a second piece that rides along with it. Both are
needed together; neither alone produces a working, testable scenario.

## 3. The two coupled pieces to build

**(a) Defer a composite FK into a cyclic table, instead of rejecting it.** In `buildFkTransformer`'s
composite branch (the `if (cyclicTables.contains(parentTable))` that throws "not yet supported"),
change it to defer: insert a type-appropriate placeholder into **each** child FK column (as the
single-column path already does via `getPlaceholderForType`), and record a pending pass-2 resolution
carrying the full composite lookup key (all sibling child-column values, ordered to the parent PK)
and which child column this is. The per-column composite transformer runs once per child column, so
the deferral bookkeeping must dedupe to **one** resolution per composite FK per row, or carry enough
to set every child column in pass 2.

**(b) Resolve it in pass 2, keyed on the (possibly composite) PK of the row being updated.** The
pending record needs: the child table, that row's **full** target PK (columns + values — a composite
PK yields `WHERE col1 = ? AND col2 = ?`), the composite FK's child columns, and the source composite
lookup key. Resolution: `keyStore.get(parentTable, sourceCompositeKey)` → the parent's new
`CompositeKey` → set each child FK column to the matching `components()[i]`, in one `UPDATE`.

This means `BulkDatabaseLoadStage.DeferredUpdate` (or a new sibling record) must carry:
`List<String> pkColumns`, `List<Object> pkValues` (the row to update — a one-element list for a
single PK), and a set of `(fkColumn → parent-PK component index)` for the composite FK, plus
`referencedTable` and the source composite key. `resolveDeferredCyclicFKs` builds `UPDATE child SET
fk1 = ?, fk2 = ? WHERE <full PK>`.

The existing single-column cyclic path must keep working unchanged — ideally the composite path is
an additive branch, not a rewrite of the single-column one.

## 4. Test

Add `CompositePkCyclicFkE2ETest` (Testcontainers). The schema must put a composite-PK table **in** a
cycle via a composite FK — e.g. two tables whose composite FKs reference each other, or a
self-referential composite FK:

```sql
CREATE TABLE node (
    tenant_id INT    NOT NULL,
    node_id   BIGINT NOT NULL,
    parent_tenant INT,
    parent_node   BIGINT,
    PRIMARY KEY (tenant_id, node_id),
    FOREIGN KEY (parent_tenant, parent_node) REFERENCES node(tenant_id, node_id)  -- composite self-ref
);
```

Seed a small in-tenant tree/cycle so at least one composite FK is a forward reference at load time
(mutual pair, as `CyclicFkE2ETest` does for the single-column case). Classify `tenant_id`/`node_id`
as `PRIMARY_KEY` and `parent_tenant`/`parent_node` as `FOREIGN_KEY` to `node`. Assert: the run
succeeds; row count preserved; no placeholder survives in either FK column; every non-null
`(parent_tenant, parent_node)` resolves to a real `(tenant_id, node_id)` in the target. This test
fails today (hits the line-468 guard) and passes once §3 is done.

## 5. Definition of done

- `./gradlew build` green (compile, all tests, the strict `Xdoclint:all` javadoc gate).
- New E2E passes; `CyclicFkE2ETest` (single-column self-ref) and `CompositeKeyE2ETest` (composite
  PK/FK, non-cyclic) still pass — the single-column cyclic path and the non-cyclic composite path
  must not regress.
- The two fail-closed guards this replaces (the composite-FK-into-cyclic message at line ~468 and
  the composite-PK row guard) are removed only for the now-supported case; genuinely unresolvable
  cases (e.g. a missing key translation) still fail closed with a clear message.
- Delete this task file and its root `PLAN.md` entry in the same commit, naming the covering test.
  If `docs/spec/incognito.md` §5.2 states the combination is unsupported, update it.

## 6. Risk note

This touches the core cyclic-load machinery (placeholder insertion, `CyclicFkException` deferral,
the two-pass `UPDATE`) and the composite-key store convention (Appendix C) at once. It is the most
intricate remaining v1.0 item. Land it behind the two regression tests above and verify each step
with a revert-and-confirm-fail check.
