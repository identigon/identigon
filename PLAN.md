# Identigon - Implementation Plan

Single ranked backlog for the whole monorepo. Entries are **deleted** when done, never annotated -
a plan that accumulates completed items stops being read. **One paragraph each** - see
[DOC-MAP.md](DOC-MAP.md). Optionally tagged `**Project:**`
(`alterego` / `incognito` / `effigies`) for work scoped to one subproject; an untagged entry is
cross-cutting or applies to no single subproject. See the root `CHANGELOG.md` for what's already
shipped.

## Outstanding

- [ ] **Project:** effigies - **`scaffold` should emit strategy stubs, not just `role:`.**
  `ScaffoldCommand.writeScaffold` writes only a `role:` TODO per column; the decisions that
  determine output quality (`directIdStrategy` for `DIRECT_ID`, `distinguishing` for `SENSITIVE`,
  `references` for `FOREIGN_KEY`) are left for the author to hand-write from scratch even though
  `PolicyInferrer` and `SchemaInspector` already know enough to suggest them (the matched
  heuristic, the resolved FK target). Emit a commented TODO stub with the suggestion inline for
  each, the same "suggest, never assign" pattern already used for `role:`. More pressing now that
  `directIdStrategy` fails closed (ADR 29): an un-stubbed scaffold is a hard stop, not a silent
  gap.
- [ ] **Project:** incognito - **Fail-closed policy validation should accumulate across all
  tables, not stop at the first.** `SchemaDiscoveryStage.validateTablePolicy` already collects
  every unclassified column *within* one table before throwing, specifically so the author fixes
  them in one pass - but `process`'s outer loop over tables throws on the first table with a
  problem, so a schema with unclassified columns in several tables still takes one run per table to
  discover them all. Collect across every table and throw once, listing all of them.
- [ ] **Project:** effigies - **A `validate` command: check a policy against a schema with no
  target database or data movement.** `SchemaDiscoveryStage.validateTablePolicy`'s fail-closed
  messages are the best diagnostics in the tool but are only reachable by committing to a full
  `run`. A `identigon validate --policy ./policy.yaml --source-url ... --source-user ...`
  subcommand - schema inspection plus policy validation only, no target connection, no load - would
  make every fail-closed error much cheaper to iterate against while authoring, and gives CI a
  pre-flight check for a policy going stale after a schema migration (pairs with the existing
  `dpia-report.json`-based CI gate).
- [ ] **Project:** effigies - **`RunCommand` should validate the salt length before connecting to
  anything.** `AlterEgo`'s builder requires a salt of at least 16 bytes but only enforces it deep
  inside pipeline construction; `RunCommand` currently null-checks `IDENTIGON_SALT` but not its
  length, so a short salt (`persistent`/`reproducible` mode) fails mid-pipeline rather than at the
  same point the missing-salt check already fires. Add the length check next to the existing null
  check.
- [ ] **Publish the `ColumnRole` vocabulary as a docs page.** The scaffold's TODO comment says "see
  the role vocabulary" (`ScaffoldCommand`) but nothing in `docs/` states it - only `ColumnRole`'s
  Javadoc does. Render its nine usable values and five `RESERVED (post-v1.0)` values (which fail
  fast if assigned) as a page under `docs/` an author can actually reach.
- [ ] **Project:** incognito - **State that `GENERATED ALWAYS AS IDENTITY` is not a "generated
  column."** `SchemaInspector`/`SchemaDiscoveryStage` only treat JDBC's `IS_GENERATEDCOLUMN`
  (computed `GENERATED ALWAYS AS (expr)`) as generated and excluded from classification; identity
  primary keys (`GENERATED ALWAYS AS IDENTITY`) are tracked separately (`identityColumns`) and
  still require a role. Both are spelled "generated" in PostgreSQL DDL, and this is easy to assume
  wrongly. One clarifying sentence in `docs/spec/incognito.md`.
- [ ] **Project:** effigies - **CLI ergonomics: per-subcommand `--help`, `scaffold --force`, UTF-8
  stdout.** Three small usability gaps found by hitting them: `EffigiesCli.run` only matches
  `help`/`-h`/`--help` as `args[0]`, so `discover --help` falls through to `DiscoverCommand` and
  prints its usage line only as a side effect of missing required flags; `scaffold` refuses to
  overwrite an existing output file with no `--force`, so re-scaffolding after a schema change
  needs a manual `rm`; and `main()` constructs `System.out`/`System.err` without an explicit UTF-8
  charset, so `§` (used throughout SPEC-referencing error messages) renders as `?` in a
  POSIX-locale environment (common in minimal containers/CI images).
- [ ] **A new `workflow_dispatch` release workflow, tagging + publishing `alterego.jar`,
  `incognito.jar` and `identigon.jar` to GitHub Packages and as attested GitHub Release assets
  (ADR-0028).** Split effigies' own `jar` task back to
  a normal thin jar (today's `effigies/build.gradle.kts` overrides it to *be* the fat merge, so
  there is currently no artifact containing only effigies' own classes), and move the fat-jar
  assembly to a new `identigonJar` task in the **root** `build.gradle.kts` - not inside effigies.
  `identigon.jar` is named for `rootProject.name`, not for the effigies subproject; it represents
  the whole monorepo, which only root can legitimately speak for, and this is root's established
  role for monorepo-wide facts already (`PLAN.md`, `CHANGELOG.md`, `docs/adr/`). The root task
  resolves a detached configuration depending on `project(":effigies")` (incognito/alterego arrive
  transitively, so root never names them directly) and otherwise does exactly what today's
  `tasks.jar` block does - same manifest, same LICENCE/NOTICE handling. Give effigies a
  `publishing {}` block for its now-thin jar (`org.identigon:effigies`, accurate POM, no
  javadoc/sources) so `./gradlew publish` covers it too, same shape as alterego/incognito; the fat
  jar is also published there, under that same coordinate with a `standalone` classifier, not as
  the primary artifact. Add `.github/workflows/release.yml` (`workflow_dispatch`, a required `tag`
  input naming an *existing*, already-pushed release tag - tag creation itself stays a manual,
  local, SSH-signed step, since CI cannot and should not hold the maintainer's personal signing
  key, discovered while implementing this): it checks out that tag, cross-checks the version it
  encodes against `baseVersion` in `build.gradle.kts`, builds, publishes the release version, then
  reuses that same build's output - renamed, unversioned - as `gh release upload` assets on a
  GitHub Release it creates for that tag, plus an `actions/attest` step over the jars (not
  `actions/attest-build-provenance` - as of its own v4 that's just a wrapper over
  `actions/attest`, and its README now points new implementations at the latter). `main.yml`'s
  existing `publish` job is untouched; the release ritual must still push the version-bump commit
  and let that ordinary build finish (publishing it as a SNAPSHOT) *before* tagging, or `main.yml`
  could publish the release version first and leave `release.yml`'s own publish step rejected.
- [ ] **Get Javadoc doclint enforcement (`Xdoclint:all`/`Xwerror`) working on every subproject,
  not just wherever a javadoc jar happens to be published.** Surfaced while implementing the item
  above: giving effigies a `publishing {}` block (no `withJavadocJar()`) exposed that the root
  `build.gradle.kts`'s shared doclint guard only ever applied where `withJavadocJar()` had been
  called (alterego, incognito) - not a deliberate scope decision, just what "wherever a subproject
  publishes one" happened to mean before effigies could publish anything at all. Effigies' own
  `javadoc` task currently has real violations (e.g. `PolicyInferrer`'s public class/constructor/
  method all lack doc comments) that strict mode would catch. Two things to do together: fix the
  existing gaps, and decide + implement how enforcement should actually be scoped going forward -
  every subproject unconditionally (Javadoc completeness is a code-quality question independent of
  whether a javadoc jar ships), or something narrower. `./gradlew javadoc` at the root should be
  the one command that exercises this everywhere it's meant to apply.
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
- [ ] **Project:** incognito - **Remove `PolicyInferrer` and
  `AnonymisationPolicy.Builder.autoInfer(boolean)` (committed, next major).** Both are
  `@Deprecated(forRemoval = true)`. Inference is authoring, not execution - fail-closed means it
  never affected engine output - and the maintained version now lives in `effigies`' own
  `PolicyInferrer`; this copy only survives for the fail-closed error message's diagnostic hint.
  See `docs/adr/0023-authoring-above-the-engine.md` for the reasoning and
  `docs/adr/0024-lockstep-versioning.md` for the version-bump mechanics.
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
