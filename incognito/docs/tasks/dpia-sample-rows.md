# Task: illustrative sample rows in the DPIA artefact

Status: not started (build-time handoff; delete once implemented and green). Its prerequisite — the
`JsonWriter` + text-block emitter refactor — is **done** (see `DpiaArtefactEmitter` / `JsonWriter`),
so this can proceed directly.

Add a small **"Sample rows (illustrative)"** table under each table's report, showing **3 synthetic
rows** so the reader can see at a glance what the data looks like *after* transformation —
fabricated columns show a generated example value; kept/link/inherited columns show a placeholder.

Two properties make this safe and low-maintenance, and both must be preserved:

- **Generated, never hand-authored.** Each fabricated cell is produced by calling AlterEgo's real
  public methods, so the illustrated format can never silently drift from what the library actually
  produces. The only parallel code is a small `strategy → which method` switch, which the Java
  compiler forces to stay exhaustive over each enum.
- **Synthetic, never real data.** Cells are generated from fixed dummy seeds with a **fixed,
  non-secret example salt** — unrelated to the run's real salt (which is destroyed before the
  emitter runs anyway). The rows correspond to no real subject, so there is no co-occurrence and no
  disclosure question. Caption every such table *illustrative — synthetic data, not this run's
  rows*.

Steps, in order:

**Step 1 — record + schema.** Add `java.util.List<String> examples` as the last component of
`AnonymisationReport.ColumnAction` (`api` package) — the 3 sample values for that column, in row
order — with a Javadoc `@param` (the doclint gate requires it). Update the `ColumnAction` line in
the SPEC §7 schema block to match. The compiler then points you at the two call sites to fix:
`AnonymisationReportBuilder` (Step 2) and `DpiaArtefactEmitterTest` (Step 4). (Storing the samples
per column keeps generation simple; the emitters transpose columns×samples into rows in Step 3.)

**Step 2 — generate them in `AnonymisationReportBuilder`.** Add the two helpers below. Build **one**
throwaway AlterEgo for the whole report by wrapping the existing table-building loop in `try (var ex
= exampleAlterEgo()) { … }` (it is `AutoCloseable`; this zeroes the example salt on exit). Where
each `ColumnAction` is constructed, also build its samples and pass them in:

```java
List<String> examples = new java.util.ArrayList<>();
for (int i = 0; i < SEEDS.size(); i++) examples.add(exampleCell(ex, colPol, SEEDS.get(i), i));
columnActions.add(new AnonymisationReport.ColumnAction(colName, colPol.role(), transformation, examples));
```

The helpers (copy verbatim; adjust imports to the file's style). `exampleCell` **mirrors the branch
order of the existing `transformation` switch** so the two never diverge:

```java
// A fixed, NON-SECRET salt used only for illustrative examples. It protects nothing and is unrelated
// to the run's real salt, so examples are deterministic, reproducible, and have zero linkage to any
// real or run data.
private static final byte[] EXAMPLE_SALT =
    "incognito-illustrative-examples".getBytes(java.nio.charset.StandardCharsets.UTF_8);
private static final java.util.List<String> SEEDS = java.util.List.of("sample-a", "sample-b", "sample-c");

/** A throwaway AlterEgo for illustrative examples only; caller must close it. Locale is fixed to the
 *  library default — examples are illustrations, so the run's exact locale is not needed. */
private static org.identigon.alterego.AlterEgo exampleAlterEgo() {
    return org.identigon.alterego.AlterEgo.builder()
        .salt(EXAMPLE_SALT.clone())
        .locale(java.util.Locale.UK)
        .rawMappingKeys(false)
        .mappingStore(new org.identigon.alterego.store.InMemoryMappingStore())
        .build();
}

/** One illustrative cell for a column at row `i`. Never touches real data. Any generation failure
 *  degrades to a placeholder rather than breaking the report. */
private static String exampleCell(org.identigon.alterego.AlterEgo ex,
        org.identigon.incognito.policy.ColumnPolicy colPol, String seed, int i) {
    try {
        ColumnRole role = colPol.role();
        if (role == ColumnRole.PAYLOAD) return "‹kept›";
        if (role == ColumnRole.FOREIGN_KEY) return "‹link›";
        if (role == ColumnRole.INHERITED_ATTRIBUTE) return "‹inherited›";
        if (role == ColumnRole.SENSITIVE) {
            if (Boolean.FALSE.equals(colPol.distinguishing())) return "‹kept›";
            if (colPol.redactionStrategy() != null) return switch (colPol.redactionStrategy()) {
                case MASK -> ex.mask('*', 0).apply(seed);
                case CLEAR -> "(cleared)";
                case CONSTANT -> "(fixed value)";
            };
            if (colPol.quasiIdStrategy() != null) return shiftedDate(ex, i);
            return "(redacted)";
        }
        if (role == ColumnRole.PRIMARY_KEY) {
            if (colPol.surrogateStrategy() == SurrogateStrategy.UUID_V4)
                return java.util.UUID.nameUUIDFromBytes(
                    seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            if (colPol.surrogateStrategy() == SurrogateStrategy.PASSTHROUGH_SURROGATE) return "‹kept›";
            return String.valueOf(1001 + i); // SEQUENTIAL_LONG or null
        }
        if (role == ColumnRole.QUASI_ID) return shiftedDate(ex, i);
        if (role == ColumnRole.DIRECT_ID || role == ColumnRole.UNIQUE_CANDIDATE_KEY) {
            DirectIdStrategy s = colPol.directIdStrategy();
            if (s == null) return "Example-" + seed;
            return switch (s) {
                case ALTEREGO_NAME -> ex.fullName().apply(seed);
                case ALTEREGO_FIRST_NAME -> ex.firstName().apply(seed);
                case ALTEREGO_LAST_NAME -> ex.lastName().apply(seed);
                case ALTEREGO_ORGANISATION -> ex.organisationName().apply(seed);
                case ALTEREGO_CITY -> ex.city().apply(seed);
                case ALTEREGO_STREET_ADDRESS -> ex.streetAddress().apply(seed);
                case ALTEREGO_POSTCODE -> ex.postcode().apply(seed);
                case ALTEREGO_EMAIL -> ex.emailAddress().apply(seed);
                case ALTEREGO_PHONE -> ex.phoneNumber().apply(seed);
                case ALTEREGO_DOMAIN -> ex.domainName().apply(seed);
                case ALTEREGO_URL -> ex.url().apply(seed);
                case ALTEREGO_GENERIC -> "Example-" + seed;
            };
        }
        return "‹kept›";
    } catch (RuntimeException e) {
        return "‹example unavailable›";
    }
}

private static String shiftedDate(org.identigon.alterego.AlterEgo ex, int i) {
    return ex.shiftDate(org.identigon.alterego.AlterEgo.DateField.MONTH)
             .apply(java.time.LocalDate.of(1984, 1 + i, 15)).toString();
}
```

Use the literal guillemets `‹ ›` for placeholders **deliberately**: they contain no `&`, `<`, or
`>`, so `htmlEscape` leaves them intact — do not switch to `<kept>`, which would render as
`&lt;kept&gt;`. The method names above are exactly those `TableTransformLoadStage` calls (see its
DIRECT_ID switch), so if AlterEgo renames one, both fail to compile and get fixed together — that is
the anti-drift property; keep the two in step.

**Step 3 — render the rows in all three formats.** The samples are stored per column, so HTML and
Markdown transpose them into rows; JSON just carries them per column. Render only when the table has
columns and non-empty `examples`.

- **JSON** (`emitJson`, per-table `columns` objects): after `transformation`, add an array
  `jw.name("examples").beginArray(); for (var e : ca.examples()) jw.value(e); jw.endArray();`.
- **HTML** (`emitHtml`, after the existing "Column actions" table for the table): emit a new
  `<table>` with `<caption>Sample rows (illustrative — synthetic data, not this run's
  rows)</caption>`, a header row of `<th>htmlEscape(ca.column())</th>` for each column, then for `i`
  in `0..2` a `<tr>` of `<td>htmlEscape(ca.examples().get(i))</td>` for each column.
- **Markdown** (`emitMarkdown`, after the "Column Actions" table): a `#### Sample Rows
  (Illustrative)` heading with an italic note `*Synthetic data showing each column's transformation,
  not this run's rows.*`, then a table whose header is the column names, a `|---|` separator with
  one cell per column, and 3 rows built the same way (`ca.examples().get(i)` per column).

**Step 4 — tests + DoD.** Update `DpiaArtefactEmitterTest.sampleReport()`'s three `ColumnAction`
constructions to pass a 3-element list (e.g. `List.of("a@example.com", "b@example.com",
"c@example.com")`, `List.of("‹kept›", "‹kept›", "‹kept›")`, …); add one assertion per format that
the "Sample rows"/"Sample Rows" table (or JSON `"examples"`) renders. Existing assertions keep
passing (a new table/field removes no existing substring). `./gradlew build` green, including the
javadoc gate.

**Scope note:** examples use a fixed locale (`Locale.UK`, the library default) purely for
simplicity; threading the run's actual locale into the throwaway AlterEgo is a later refinement, not
needed for a first cut.
