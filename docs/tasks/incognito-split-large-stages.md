# Task: break up `TableTransformLoadStage` and `VerificationStage`

Status: not started. Delete once done and green.

## 1. Why

Each is about 1,200 lines, and most of that sits in a few very long methods. PMD's size and
complexity rules (`GodClass`, `CognitiveComplexity`, `NcssCount`, ...) are excluded in
`config/pmd/ruleset.xml` to let them pass. The upcoming quoting, schema and snapshot work
(`docs/tasks/incognito-identifier-quoting.md`, `incognito-schema-qualified-names.md`,
`incognito-consistent-source-snapshot.md`) has to touch nearly every SQL statement in both, so
splitting them first makes those changes smaller and easier to review.

This is a refactor: no behaviour change, and the Testcontainers E2Es and benchmark suites are the
safety net - run them with Docker available, not just the no-Docker subset.

## 2. `VerificationStage` (process is ~470 lines)

`process` runs six independent checks in sequence, each with its own connections and its own slice
of the report:

1. FK integrity on the target
2. fictionality of fabricated columns
3. the distinguishing lint (source)
4. per-period volume (source vs target)
5. DIRECT_ID source-value survival
6. structural uniqueness (source)

Suggested shape: one small class per check behind a common interface
(`check(context, plan, metadata, reportBuilder)`), with `VerificationStage` only sequencing them.

The nine `verify*Fictionality` methods (e-mail, postcode, domain, URL, phone, NINO, NHS number,
passport, driving licence) are near-copies of one query -
`COUNT(*) WHERE col IS NOT NULL AND NOT (<reserved-space condition>)` - differing only in the
condition. Replace them with one method driven by a `DirectIdStrategy -> condition` table; the
reserved-space constants already sit at the top of the class.

## 3. `TableTransformLoadStage`

- `processTable` (~220 lines) mixes connection and transaction handling, the per-row loop, PK/FK
  key-store bookkeeping, inherited-attribute publishing, and cyclic-FK deferral. Pull the per-row
  work into its own class; leave `processTable` owning connections and the loop.
- `ColumnTransformer.transform` takes eight parameters, most of them the same for every column of a
  row. Pass one per-row context record (source row, source PK, counter, record scope) instead.
- The per-role `build*Transformer` methods (PK, FK ~145 lines, DIRECT_ID, redaction, QUASI_ID ~145
  lines) are independent factories; each can move to its own class next to `ColumnTransformer`.
- `getDialectHandler` is duplicated in `IncognitoCleanUpHandler`; one factory (e.g. on
  `DialectHandler`) should serve both.

## 4. Smaller tidy-ups to fold in

- `SchemaInspector` groups FK columns as `Object[]` tuples; use a record.
- Fully qualified `java.util.*` and `org.identigon...` names are used instead of imports throughout
  incognito's main code (PMD's `UnnecessaryFullyQualifiedName` is excluded to allow it).
- 34 incognito test classes each carry their own private `SimpleDataSource` record; one shared test
  helper would do.

Re-enable whichever PMD exclusions the split makes unnecessary.
