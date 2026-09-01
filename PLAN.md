# Identigon - Implementation Plan

Single ranked backlog for the whole monorepo. Entries are **deleted** when done, never annotated -
a plan that accumulates completed items stops being read. **One paragraph each** - see
[DOC-MAP.md](DOC-MAP.md). Optionally tagged `**Project:**`
(`alterego` / `incognito` / `effigies`) for work scoped to one subproject; an untagged entry is
cross-cutting or applies to no single subproject. See the root `CHANGELOG.md` for what's already
shipped.

## Outstanding

- [ ] **Project:** incognito - **`QUASI_ID` synthesis on a character type carries no fictionality
  guarantee without an explicit `directIdStrategy` hint, and nothing warns for it.**
  `isSynthesisableType` treats `VARCHAR`/`CHAR` as synthesisable because they shape-preserve, not
  because a fictional generator exists - so a hint-less `QUASI_ID` (e.g. `postcode`) passes the
  fail-closed guard and silently shape-fabricates, exactly the gap ADR-0029 closed for `DIRECT_ID`.
  `effigies scaffold` compounds it: `writeRoleStub` has no `QUASI_ID` branch, so no
  `directIdStrategy:` TODO stub is ever suggested, even for a column its own heuristics identified
  as a postcode. Cheapest fix: add the scaffold stub. Stricter fix, mirroring ADR-0029: require an
  explicit strategy on a `QUASI_ID` whose type has no fictional generator, so shape-fabrication
  becomes a declared choice rather than a default. Found by external tutorial feedback.
- [ ] **Project:** incognito - **`YamlPolicyParser` accepts unrecognised keys within a
  table/column block instead of rejecting them.** Every field is read via
  `colNode.get("exactName")` with no validation against a known key set, so a typo on an optional
  key (e.g. `jitterdays` for `jitterDays`) is silently dropped, an internal default applies, and
  `validate` reports success even though the run's behaviour now diverges from the declared
  policy. Fix: reject unrecognised keys, naming the offending key and column; carve out an
  explicit allow-list for intentionally-retired keys (`autoInfer` today) so back-compat tolerance
  stays deliberate rather than blanket. Found by external tutorial feedback.
- [ ] **Project:** incognito - **`fictionalityVerified` in the DPIA report reads `true` for
  columns no fictionality check actually covers.** It's computed in `VerificationStage` as "table
  policy present and no specific check failed" - so `ALTEREGO_GENERIC` (declared deliberately,
  e.g. `bank_account`) and hint-less `QUASI_ID` shape-fabrication (see the entry above) both report
  `Fictionality Verified: true` with no supporting check having run for that column. Scope the flag
  per column, or rename it to reflect that it covers survival/misdeclaration checks rather than a
  fictionality guarantee. Found by external tutorial feedback.
- [ ] **Project:** effigies - **`quickstart/README.md` never mentions `validate`.** Both driver
  scripts run `discover -> scaffold -> run`; the README pitches its `setup`/`run` split as "the
  real authoring workflow", which is exactly where the cheap no-target-connection feedback loop
  belongs. Found by external tutorial feedback.
- [ ] **`pg_dump` guidance for creating the target database is documented on the site, not in this
  repo.** Getting Started's `pg_dump --schema-only --no-owner --no-privileges` never made it into
  any `.md` here, so a reader arriving via GitHub - where the "schema-identical target database"
  prerequisite is stated with no method - doesn't see it. Getting this wrong (omitting
  `--schema-only`) is silently destructive, since `run` loads into the target rather than replacing
  it. A line in `README.md` pointing at the site would cover it. Found by external tutorial
  feedback.
- [ ] **Project:** effigies - **`discover`/`scaffold` report JDBC's type names, not PostgreSQL's**
  (a `BOOLEAN` column shows as `type: BIT`, `TEXT` as `type: VARCHAR`). Not a defect - both are
  correct at the JDBC layer - but the type is an input to choosing a strategy, so an author
  classifying a column sees a name that doesn't match the DDL. Worth a sentence somewhere noting
  whose type names these are. Found by external tutorial feedback.
- [ ] **Project:** effigies - **Revisit excluding `effigies.jar` from the GitHub Release assets.**
  ADR-0028 deliberately left the thin `effigies.jar` out of the Release asset set (`alterego.jar`,
  `incognito.jar`, `identigon.jar` only) on the reasoning that it can't run standalone - anyone
  wanting effigies as a library uses GPR (`org.identigon:effigies`), and anyone wanting to run it
  uses the fat `identigon.jar`. Revisit this decision before treating it as settled.
- [ ] **Project:** effigies - **JaCoCo.** Optional, consistency-only with `alterego`/`incognito`'s
  coverage reporting.
- [ ] **Project:** effigies - **A non-interactive "authoring session" mode.** Runs discover ->
  scaffold -> (agent) -> run in one invocation, with the DPIA report fed back for iteration.
- [ ] **Project:** effigies - **Support for engines `incognito` adds beyond PostgreSQL.** No change
  needed here when it lands.
- [ ] **Project:** effigies - **Revisit `quickstart/` and the Agent Skill once
  `incognito`'s policy-API backlog lands.** Several `alterego` capabilities `incognito` doesn't
  expose yet (remaining identifier generators, a bank-account generator, `RecordScope` cross-field
  coherence, jitter/clamp knobs, a `pattern(String)` strategy - see the matching
  `incognito`-tagged entries above) are candidates to fold into the quickstart policy and teach the
  `identigon-policy-author` skill to suggest, as each lands.
- [ ] **Project:** incognito - **Composite PK + cyclic FK together.** Currently fails closed with a
  clear message rather than corrupting data; not exercised by any benchmark. Bigger than "widen the
  pass-2 `UPDATE` to key on every PK column" - the real gap is deferred *composite-FK* resolution
  into a cyclic table (a composite-PK table can only be in a cycle if a composite FK references it,
  which hits a separate guard first). See
  `docs/tasks/incognito-composite-pk-cyclic-fk.md` for the full analysis and handoff.
- [ ] **Project:** alterego - **`ServiceLoader`-based strategy/dictionary packs for additional
  countries.** Post-v1 extensibility: distribute new-country dictionaries and strategies as
  separate artifacts rather than bundling every country in core.
- [ ] **Project:** alterego - **Language-sensitive generation.** v1 built-ins resolve entirely by
  the locale's country; the language component steers nothing yet. Unused so far - no concrete
  need has surfaced.
- [ ] **Project:** alterego - **Pattern-language extensions: character classes and repetition
  counts.** `pattern(String)`'s `D`/`L`/`l`/`A` mini-language has no `[ABC]` character-class or
  `D{5}` repetition syntax. A genuine specification contract change (new syntax, new exception
  cases, new conformance tests) - do deliberately, not as a drive-by. `incognito`'s planned
  `ALTEREGO_PATTERN` strategy doesn't need this to land first (today's syntax is already enough to
  wire) but should be revisited to expose any richer syntax added here.
- [ ] **Project:** alterego - **External `MappingStore` modules (JDBC, Redis).** Build against the
  existing contract test once a real need appears; a local file-backed store already ships in core
  (see the file-backed-mapping-store ADR).
- [ ] **Project:** alterego - **A public codec SPI for caller-supplied value types.** The fixed
  value-type set was deliberately rejected as a public SPI (see the fixed-value-type-set ADR);
  revisit only if a real need appears.
- [ ] **Project:** alterego - **Fictional-range additions: TEST-NET IP addresses (RFC 5737).** Same
  fictional-by-default family as the existing built-ins' reserved ranges.
- [ ] **Project:** alterego - **`companyNumber()` (Companies House) - blocked, unsolved fictional
  space.** No reserved/test range and no checksum exist for UK company numbers; the only
  structurally-impossible value is zero, and mapping every company to zero is redaction, not
  pseudonymisation. A high range is time-dependent (Scotland is already at `SC770005`). Deferred
  until a reserved or never-issued range is found - full analysis in
  `docs/research/0002-alterego-fictional-ranges.md`. Has a regional element (`SC`/`NI`/plain) that
  would feed
  `UK_NATION` record coherence if it returns.
- [ ] **Project:** incognito - **Multi-edge structural fingerprints.** The shipped
  structural-uniqueness report scores one FK edge at a time; combining several edges into one joint
  fingerprint per subject is more faithful to real singling-out but harder to threshold defensibly.
- [ ] **Project:** incognito - **Attribute + structure combined findings.** A kept
  `distinguishing: false` value plus a rare FK fan-out is a stronger fingerprint than either alone;
  not modelled today.
- [ ] **Project:** incognito - **Declarative-partitioning support.** The Pagila benchmark excludes
  the partitioned `payment` table because partition children are discovered as plain tables with no
  special handling. A proper treatment recognises the parent/child relationship
  (`pg_partitioned_table`/`pg_inherits`), clones by inserting into the parent (letting Postgres
  route to partitions), and skips the children - preserving per-partition volumes.
- [ ] **Project:** incognito - **`RedisKeyTranslationStore` - a persisted, out-of-process key
  store.** v1.0 ships only an in-memory store (Redis is an explicit v1.0 non-goal). Would let key
  translation outlive a single JVM run - useful for very large clones, resuming an interrupted
  load, and cross-run surrogate stability. A persisted key store is itself sensitive and must be
  destroyed on successful completion, exactly as the salt is.
- [ ] **Project:** incognito - **No UK bank-account generator in `alterego`.** Unlike the other UK
  identifiers, there's no primitive to wire even if `DirectIdStrategy` grew a case for it. Surfaced
  authoring a bank-account column for the effigies quickstart example, which falls back to
  `ALTEREGO_GENERIC` (no fictionality guarantee) in the meantime.
- [ ] **Project:** incognito - **Temporal `QUASI_ID` jitter never touches the time-of-day
  component.** Every temporal strategy zeroes seconds or does date-only arithmetic; a `TIMESTAMP`
  column's clock time always survives exactly as-is. `alterego` already supports jittering it
  (`TimeField.HOUR`, an explicit range, a seconds half-range, `shiftInstant()`) - none of it
  reachable from a policy today.
- [ ] **Project:** incognito - **No way to clamp a jittered `QUASI_ID` date/time to "not in the
  future."** `alterego`'s `JitterOptions.max(...)` already supports an inclusive upper bound (the
  caller-supplied-bound principle - see the explicit-clamp-bounds ADR), but `incognito` never
  passes one. A wide `JITTER_DAYS` shift on a date near "today" in the source can jitter into the
  future. Narrower than a fully general clamp: cap at the run's own captured "now".
- [ ] **Project:** incognito - **`AlterEgo.pattern(String)` has no `DirectIdStrategy` equivalent.**
  No way for a policy to hand a custom shape pattern for a code-like column that isn't a
  name/email/phone/etc. A `DirectIdStrategy.ALTEREGO_PATTERN` with a `pattern` field on
  `ColumnPolicy` would close this - not blocked on `alterego`'s pattern-language extensions item,
  though richer syntax there should be revisited here if it lands.
- [ ] **Project:** incognito - **`AlterEgo.creditCardNumber()` still unused by incognito (low
  priority).** Its practical gap was closed instead via `ColumnPolicy.redactionConstant` (a fixed
  placeholder, arguably a better privacy story than per-row fabrication for a `SENSITIVE` field).
  Wiring the typed generator itself remains possible but is no longer blocking anything.
- [ ] **Project:** effigies - **JaCoCo.** Optional, consistency-only with `alterego`/`incognito`'s
  coverage reporting.
- [ ] **Project:** effigies - **A non-interactive "authoring session" mode.** Runs discover ->
  scaffold -> (agent) -> run in one invocation, with the DPIA report fed back for iteration.
- [ ] **Project:** effigies - **Support for engines `incognito` adds beyond PostgreSQL.** No change
  needed here when it lands.
- [ ] **Project:** effigies - **Revisit `quickstart/` and the Agent Skill once
  `incognito`'s policy-API backlog lands.** Several `alterego` capabilities `incognito` doesn't
  expose yet (remaining identifier generators, a bank-account generator, `RecordScope` cross-field
  coherence, jitter/clamp knobs, a `pattern(String)` strategy - see the matching
  `incognito`-tagged entries above) are candidates to fold into the quickstart policy and teach the
  `identigon-policy-author` skill to suggest, as each lands.
