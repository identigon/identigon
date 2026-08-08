# Task: SYNTHESISE-by-type routing (SPEC Appendix B)

Status: not started (build-time handoff; delete this file once implemented and green)

## 1. What's missing

SPEC **Appendix B** says `QUASI_ID` `SYNTHESISE` routes by the column's source type — temporal dates
shift, a postcode-shaped `VARCHAR` uses `ae.postcode()`, a city `VARCHAR` uses `ae.city()`, "other
`VARCHAR`" uses format-preserving fabrication, and **a type with no mapping and no custom strategy
fails fail-closed with `ConfigException` — never a silent passthrough.**

The current `SYNTHESISE` branch in `TableTransformLoadStage` does only two of those:

```java
Object shifted = shiftTemporalOrNull(value, dateTransform, dateTimeTransform);
return shifted != null ? shifted : strTransform.apply(value.toString());  // shape-preserving fallback
```

So **every non-temporal value** — a `VARCHAR` city, a postcode, *and* a numeric — falls through to
generic shape-preserving fabrication. Two real gaps result:

1. **No fictionality-guaranteed typed routing.** A postcode/city/etc. gets a scrambled
   shape-preserving string, not a value from a fictional vocabulary (RFC 2606, guaranteed-fictional
   postcode, …). This affects live benchmark columns today (Chinook/Northwind classify
   `city`/`region`/`state` as `QUASI_ID SYNTHESISE`).
2. **No fail-closed for unmapped types.** A numeric `QUASI_ID SYNTHESISE` is silently
   shape-fabricated instead of aborting, which SPEC Appendix B explicitly forbids ("Do not
   passthrough a real number").

Both need doing. (No benchmark currently declares `SYNTHESISE` on a numeric column, so adding the
fail-closed abort does not break the existing suite — verified.)

## 2. Design decision (settle before coding)

**How does the code know a `VARCHAR` is "postcode-shaped" vs "city" vs "street"?** The SPEC lists
these but does not say how to detect them. **Do NOT auto-detect by column name or value shape** —
that conflicts with Incognito's fail-closed, author-declares philosophy (ADR 0004: auto-inference
only *suggests*, never assigns).

Instead, the author **declares** the typed generator, reusing the existing `DirectIdStrategy` enum
as a `SYNTHESISE` hint on the `QUASI_ID` column:

```yaml
city:     { role: QUASI_ID, quasiIdStrategy: SYNTHESISE, directIdStrategy: ALTEREGO_CITY }
postcode: { role: QUASI_ID, quasiIdStrategy: SYNTHESISE, directIdStrategy: ALTEREGO_POSTCODE }
```

This needs **no new enum or policy field** — `directIdStrategy` already exists on `ColumnPolicy` and
is not currently validated against role, so it can coexist with `quasiIdStrategy` (confirm no
validator rejects the combination; there is none today). The routing rule becomes:

- hint present (`directIdStrategy != null`) → use that typed generator (postcode/city/street/org/…);
- else temporal type → shift (unchanged);
- else character type (`VARCHAR`/`CHAR`/`TEXT`) → shape-preserving fabrication (unchanged — this is
  Appendix B's "other `VARCHAR` → format-preserving" row);
- else (non-temporal, non-character, no hint) → **fail closed** with `ConfigException`.

## 3. Steps, in order

**Step 1 — route the hint in the `SYNTHESISE` branch** (`TableTransformLoadStage`, `case
SYNTHESISE`). Delegate to the existing DIRECT_ID transformer, which already reads `directIdStrategy`
and produces a guaranteed-fictional typed value — no duplication:

```java
case SYNTHESISE -> {
    if (colPolicy.directIdStrategy() != null) {
        yield buildDirectIdTransformer(colPolicy, alterEgo, tableName);  // typed, guaranteed-fictional
    }
    // ... existing temporal-shift + character shape-preserving code stays as the fallback ...
}
```

**Step 2 — fail closed for unmapped types.** For a `QUASI_ID SYNTHESISE` column with **no** hint
whose source SQL type is neither temporal nor character, abort. Prefer config-time in
`SchemaDiscoveryStage` (it has both the policy and each column's SQL type via `SchemaInspector`'s
`columnTypes`), so the run fails before any row is read:

- temporal = `Types.DATE`, `TIMESTAMP`, `TIMESTAMP_WITH_TIMEZONE`;
- character = `Types.VARCHAR`, `CHAR`, `LONGVARCHAR`, `NVARCHAR`, `NCHAR`, `LONGNVARCHAR`.

For any other type (`INTEGER`, `NUMERIC`, `BOOLEAN`, …) with `quasiIdStrategy == SYNTHESISE` and
`directIdStrategy == null`, throw `IncognitoException.ConfigException` with a message that names the
column and points the author at the fix ("declare a directIdStrategy hint or a custom strategy; SPEC
Appendix B"). If wiring a new discovery-time check is too invasive, a transform-time abort in the
`SYNTHESISE` branch (throw when `shifted == null` **and** the value is not a `String`/`CharSequence`
**and** there is no hint) is an acceptable fallback — but state which you chose.

**Step 3 — (recommended) exercise it in the benchmarks.** Add `directIdStrategy: ALTEREGO_CITY` to
the `city`/`ship_city` columns and `ALTEREGO_POSTCODE` where a postcode `QUASI_ID` exists, in the
Chinook/Northwind policy YAMLs, so those columns now get fictional values and the feature has real
coverage. (`region`/`state` have no dedicated generator — leave them shape-preserving.) Optional but
valuable; keep it a separate, clearly-labelled part so the core fix lands even if skipped.

**Step 4 — tests.**
- A no-Docker policy/discovery test: a `QUASI_ID SYNTHESISE` on a numeric column **fails closed**
  with `ConfigException` (mirror `DistinguishingLintTest`'s style if config-time, else a small E2E).
- An E2E test: a `VARCHAR` column classified `QUASI_ID SYNTHESISE` + `directIdStrategy:
  ALTEREGO_CITY` yields a value from AlterEgo's fictional-city vocabulary, not a scramble of the
  source (assert it is not equal to the source and, if practical, matches the city generator's
  shape). `WalkingSkeletonTest` or `RedactionTypeE2ETest` are good scaffolding to copy.

**Step 5 — reconcile the SPEC.** Update Appendix B to state that the typed-`VARCHAR` rows
(postcode/city/street/organisation) are selected by an author-declared `directIdStrategy` hint on
the `QUASI_ID` column (not auto-detected), and that an unmapped type with no hint and no custom
strategy fails closed. Keep the table; add the one clarifying sentence.

## 4. Definition of done

- `./gradlew build` green (compile, all tests, javadoc gate).
- A numeric `QUASI_ID SYNTHESISE` with no hint aborts with a clear `ConfigException`; a hinted
  `VARCHAR` `SYNTHESISE` produces a typed, guaranteed-fictional value.
- Existing behaviour preserved: temporal `SYNTHESISE` still shifts; an un-hinted `VARCHAR`
  `SYNTHESISE` still shape-preserves (so the current benchmarks pass unchanged unless Step 3 opts
  them in).
- `PLAN.md` Phase 4: tick the SYNTHESISE-by-type item and note the covering tests.

## 5. Scope guard

Do **not** implement column-name / value-shape auto-detection of "postcode-shaped" — the routing is
author-declared only. Do not add a new enum or policy field — reuse `directIdStrategy`. Keep the
change confined to the `SYNTHESISE` branch, one discovery-time validation, the two tests, and the
SPEC sentence.
