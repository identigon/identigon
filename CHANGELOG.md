# Changelog

Release notes for the `identigon` monorepo as a whole. `alterego`, `incognito`, and `effigies`
version together (lockstep - see any subproject's "lockstep versioning" ADR): one version number,
one tag, one entry here per release.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/): reverse-chronological, an
`Unreleased` section at the top, entries grouped under the six standard categories (`Added`,
`Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`) - a category with nothing to report that
release has no subsection. Each entry is tagged `**alterego:**`, `**incognito:**` or
`**effigies:**` for the subproject it concerns; an untagged entry is cross-cutting (build, CI, or
other repository infrastructure, not any one subproject's own behaviour).

Every release before 1.0.0 (when the three subprojects joined lockstep) is folded in below with a
project-prefixed version tag (`alterego-0.1.0`, `incognito-1.0.0`, `effigies-1.0.0`, ...) instead
of a bare number, since each subproject's own pre-lockstep numbering restarted independently and
would otherwise collide with this file's own `1.0.0` (the first _shared_ release) - most visibly,
`incognito` and `effigies` each had their own unrelated `1.0.0` before joining lockstep. Those
entries carry no per-item project tag - the version itself already scopes the whole release to one
subproject. Each subproject's standing output-stability guarantees / hard invariants, previously
repeated at the top of its own now-retired `CHANGELOG.md`, live in its `docs/spec/` member
instead - restating them here too would be the same fact in two places.

## [Unreleased]

## [3.2.0] - 2026-09-03

### Added

- **incognito:** `run` now fails closed before loading any row if any policy-covered target table
  already has data (`NonEmptyTargetGuardStage`, part of the default pipeline) - a failed run's
  compensation previously deleted such pre-existing data along with anything the run itself
  inserted (tutorial-feedback finding). `IncognitoPipeline.Builder.allowNonEmptyTarget()` opts out
  for a caller who has weighed that risk. `docs/spec/incognito.md` §8.1 updated.
- **effigies:** `run` gained `--force`, threading through to the new `allowNonEmptyTarget()`
  above. `docs/spec/effigies.md` §3 updated.
- Prettier added as a pre-commit hook for `*.md` files, complementing `markdownlint-cli2` (a
  formatter, not a linter - it doesn't replace any of markdownlint-cli2's structural rules).
  Automates the line-wrapping `markdownlint-cli2`'s `MD013` has no auto-fix for. Every `*emphasis*`
  in the repo became `_emphasis_` in the one-time bulk reformat that came with adopting it -
  Prettier's markdown printer has no config option to keep asterisks, confirmed empirically; see
  `docs/adr/0036-adopt-prettier-for-markdown-alongside-markdownlint-cli2.md`. Pinned via `npx`
  (`prettier@3.9.6`), not the `pre-commit/mirrors-prettier` repo, which is stale. Every fenced code
  block's content was verified byte-identical before/after the reformat -
  `--embedded-language-formatting=off` is load-bearing: Prettier's default tried to reformat
  embedded YAML too, and corrupted this repo's policy examples (dropped keys, emptied `{ }`
  blocks) before that flag was found and applied.

### Fixed

- **incognito:** `IncognitoCleanUpHandler.compensate` no longer runs its unconditional
  `DELETE FROM` on every table when a failure happens before `TableTransformLoadStage` ever begins
  writing (schema discovery, the non-empty-target guard above, or any other pre-flight fail-closed
  check) - previously it ran regardless, so even a failure that touched no data could destroy real
  pre-existing target data. Compensation is now a complete no-op until loading has genuinely
  started (`TableTransformLoadStage.ATTR_LOAD_STARTED`).
- **incognito:** `validate`/`run` now fail closed when a single-column `FOREIGN_KEY` declares no
  `references: { table, column }`. Previously this validated cleanly and then hit a raw
  `NullPointerException` at `run` time (`ConcurrentHashMap.get(null)` inside the key-translation
  store), since `TableTransformLoadStage.buildFkTransformer` reads the policy-declared reference
  unchecked. The new check in `SchemaDiscoveryStage.validateTablePolicy` mirrors the parent's
  structural discovery to suggest the correct block, the same way `scaffold` already does, and
  exempts composite FKs (resolved structurally, not from the policy). `docs/spec/incognito.md`
  §4.1/§7.2 updated to describe the requirement.
- **incognito:** `TableTransformLoadStage.buildFkTransformer` no longer assumes a composite FK
  targets the parent's primary key just because the parent has one. It now fails closed
  (`ConstraintException`) whenever the FK's declared columns aren't exactly the parent's PK as a
  set - previously an FK _wider_ than the PK (covering the PK plus an extra column from some other
  `UNIQUE` constraint) slipped past the old narrower-only check and crashed with an
  `ArrayIndexOutOfBoundsException` mid-load instead. A composite FK that targets the PK precisely,
  possibly in a different column order than declared, is unaffected.
- **incognito:** a single-column `FOREIGN_KEY` with no `references` declared now fails closed
  (`ConstraintException`) inside `TableTransformLoadStage.buildFkTransformer` itself, not just in
  `SchemaDiscoveryStage.validateTablePolicy`. Defence in depth only - `run`/`validate` already
  catch this earlier - for a caller that builds an `AnonymisationPolicy` and skips
  `SchemaDiscoveryStage` entirely.
- **effigies:** every CLI subcommand (`discover`, `scaffold`, `validate`, `run`) now prints the
  full cause chain on failure instead of just the outermost exception. `DefaultIncognitoPipeline`
  wraps a genuinely unexpected failure as `IncognitoException("Pipeline execution failed", e)` -
  the real diagnostic was in `e`, but the CLI only ever printed the wrapper's own message. New
  `CliErrors` utility renders `Throwable.toString()` plus one `Caused by:` line per cause beneath
  it.

### Changed

- **incognito:** `DialectHandler` gained a `bindValue(PreparedStatement, int, Object)` method
  (default: plain `setObject`). `BulkDatabaseLoadStage.insertRow` no longer hardcodes PostgreSQL's
  `Types.OTHER` coercion for `String` values (needed so a kept enum/user-type column round-trips)
  - that rule now lives in `PostgresDialectHandler.bindValue`, where a future non-Postgres dialect
    can supply its own instead of inheriting Postgres's.
- **incognito:** `BulkDatabaseLoadStage.resolveDeferredCyclicFKs` (the pass-2 `UPDATE` that
  resolves cyclic-FK placeholders) now batches instead of preparing and executing a new statement
  per deferred row. Updates are grouped by `tableName`/`pkColumn`/`fkColumn` - the parts that
  determine the `UPDATE`'s SQL text - so each distinct shape shares one `PreparedStatement` and
  batches of up to 1,000, matching `insertRow`'s own batch size. No behaviour change; a cyclic-FK
  benchmark or clone with many deferred rows previously paid one round-trip per row here.

## [3.1.0] - 2026-09-01

### Fixed

- **incognito:** `YamlPolicyParser.parse(Path)` no longer swallows the unrecognised-key diagnostic
  behind a generic "Failed to read YAML from path" message. It now rethrows a `ConfigException`
  from `parse(InputStream)` unchanged, so the CLI (`validate`/`run`) shows the actual mistyped
  key(s) instead of a message indistinguishable from a truly unreadable file.
- **incognito:** `VerificationStage` now asserts fictionality for `ALTEREGO_PHONE`, the one
  `DirectIdStrategy` with a typed generator that had no corresponding check (email, postcode,
  domain, URL, NINO, NHS number, passport number, and driving licence number were already
  covered). A fabricated value must land in one of GB's reserved Ofcom drama-number ranges
  (`GB/phone-ranges.txt`); `docs/spec/incognito.md` §4.3 corrected to describe this precisely
  (GB-specific, multi-range, and conditional on `PhoneNumberStrategy`'s default options).

## [3.0.0] - 2026-09-01

### Added

- **incognito:** `AnonymisationReport.ColumnAction` gains a `fictionalityVerified` field - `true`
  only when a typed SPEC §4.3 fictionality check actually ran and passed for that specific column;
  `false` for every other column, including a deliberately-chosen `ALTEREGO_GENERIC` strategy,
  which is a legitimate choice (ADR 21/29) but carries no guarantee to verify. Previously only
  `TableReport.fictionalityVerified` existed, a table-level "no verification failure found" signal
  that could read `true` for a table holding an unguaranteed column. `VerificationStage`'s
  fictionality checks also now cover a `QUASI_ID SYNTHESISE` column carrying a `directIdStrategy`
  hint, not just `DIRECT_ID` - both route through the same typed generator (ADR 31), so both get
  the same check. All three DPIA formats (JSON/HTML/Markdown) surface the new per-column field,
  and the table-level one is now labelled to make clear what it does and doesn't cover.
- **effigies:** `scaffold` now suggests a `directIdStrategy` hint for an inferred `QUASI_ID`
  column that has one (`postcode` via `POSTCODE_PATTERN` today), mirroring the existing
  `DIRECT_ID` stub - closes the gap where a scaffolded policy could land on incognito's new
  QUASI_ID fail-closed guard below with no suggestion of how to fix it.
- **effigies:** `effigies/README.md`'s `run` section now explains how to create the target
  database. `pg_dump --schema-only --no-owner --no-privileges`, loaded into a fresh database, with
  a note that omitting `--schema-only` is destructive (it dumps real rows into what's meant to
  become the anonymised target) - the guidance already existed on the project site's Getting
  Started page but never made it into this repo, so a reader arriving via GitHub had the
  prerequisite named with no method.

### Changed

- **incognito:** a character-type `QUASI_ID` using `SYNTHESISE` now requires an explicit
  `directIdStrategy` hint. Previously a `QUASI_ID` on a `VARCHAR`-family column with no hint
  silently fell back to shape-preserving fabrication (no fictionality guarantee, ADR 21) even with
  the default `SYNTHESISE` strategy; it now fails closed at schema-discovery time instead, the
  same treatment ADR 29 already gave `DIRECT_ID`. `ALTEREGO_GENERIC` remains fully valid as an
  explicit choice; temporal `SYNTHESISE` columns (`DATE`/`TIMESTAMP`) are unaffected - they keep
  their type-matched shift primitive and need no hint. See
  `docs/adr/0031-declared-direct-id-strategy-for-quasi-id-synthesise.md`.
- **incognito:** `YamlPolicyParser` now rejects unrecognised keys at the policy root, inside a
  table block, or inside a column block, instead of silently ignoring them. Previously a typo on
  an optional key (e.g. `jitterdays` for `jitterDays`) was dropped with no signal, an internal
  default applied, and the run diverged from what the policy declared; `validate` reported success
  regardless. Every issue across the whole file is now collected into one `ConfigException`,
  matching `SchemaDiscoveryStage`'s "fix all at once" convention. `autoInfer` remains the one
  tolerated exception, for back-compat with a pre-v2.0.0 `policy.yaml`.
- **effigies:** `quickstart/`'s driver scripts and README now run `validate` between authoring and
  `run`. `run-quickstart.sh`/`.ps1`'s one-shot demo is `discover -> scaffold -> validate -> run`
  (previously skipped `validate`), and `./run-quickstart.sh run` (the real authoring workflow's
  second half) validates the finished policy before anonymising with it. The manual walkthrough in
  `quickstart/README.md` gained a matching "Validate the policy" step between authoring and
  running. `validate` existed already (v2.0.0) but the quickstart never demonstrated the
  no-target-connection, no-data-movement feedback loop it exists to provide.
- **effigies:** `discover` and `scaffold` now disclose that reported column types are JDBC's own
  names, not necessarily the database's. A `BOOLEAN` column was reported as `type: BIT`, `TEXT` as
  `type: VARCHAR` - correct at the JDBC layer, but an author classifying a column saw a name that
  didn't match the DDL, with nothing explaining why. `discover`'s console output and `scaffold`'s
  generated `policy.yaml` both gained a one-line note; `docs/spec/effigies.md` documents it too.

## [2.0.0] - 2026-09-01

### Added

- **alterego:** now also published as a `GitHubPackages`-mirrored jar attached to the GitHub
  Release, once a release is cut via the new `.github/workflows/release.yml`. No change to the
  Maven artifact itself (`org.identigon:alterego`); this only adds a second, token-free way to
  fetch the plain jar directly (see effigies below for why one was needed).
- **incognito:** same GitHub Release mirroring as alterego, above - no change to
  `org.identigon:incognito` itself.
- **incognito:** new public constant `IncognitoPipeline.MIN_SALT_BYTES`, re-exported from
  `AlterEgo`'s own minimum-salt-length requirement, so a caller can validate a
  `persistentSalt`/`reproducible` salt up front instead of waiting for `build()` to reject it (see
  effigies below for the first caller).
- **incognito:** `SchemaDiscoveryStage.validate(List<TableMetadata>, AnonymisationPolicy)` is now
  public. The same fail-closed check `process` already ran, pulled out so it is callable directly
  against an already-inspected schema - no target connection, no `PipelineContext`, no
  dependency-graph computation. `process` now calls it internally; no behaviour change there. See
  effigies' new `validate` command below for the first caller.
- **effigies:** `effigies` is now published to GitHub Packages too
  (`org.identigon:effigies`), the same way alterego/incognito already are - a normal thin jar with
  an accurate POM (no javadoc/sources jar). Previously effigies had no `publishing {}` block at
  all.
- **effigies:** `scaffold` now suggests `directIdStrategy`, `distinguishing`, `references` and
  `surrogateStrategy`, not just `role`. Previously only `role:` carried a suggestion comment,
  leaving every other decision that determines output quality to be hand-written from scratch.
  Now: a `DIRECT_ID` suggestion with an unambiguous heuristic also suggests the matching
  `directIdStrategy`; a `SENSITIVE` suggestion suggests filling in `distinguishing`; a column
  `SchemaInspector` already knows is structurally a primary or foreign key is suggested as such (a
  fact, not a heuristic guess) with `surrogateStrategy`/a pre-filled `references: {table, column}`
  respectively. Still "suggest, never assign" throughout - every suggestion is a comment, nothing
  is written into a real key. Also fixes `PolicyInferrer`'s `CREDIT_CARD_PATTERN`: it suggested
  `DIRECT_ID`, but a card number has no typed fictional-generator and is conventionally redacted -
  it now suggests `SENSITIVE`, matching how `incognito` actually treats one.
- **effigies:** `scaffold` gained `--force` to overwrite an existing output file (previously
  always refused, so re-scaffolding after a schema change needed a manual `rm` first).
- **effigies:** new `validate` command. `SchemaDiscoveryStage`'s fail-closed messages were the
  tool's best diagnostics but only reachable by committing to a full `run`. `validate --policy
./policy.yaml --source-url ... --source-user ...` checks a policy against the source schema with
  no target connection and no data movement - the same errors `run` would raise, cheaper to
  iterate against while authoring, and usable as a CI pre-flight check for a policy going stale
  after a schema migration. The `identigon-policy-author` Agent Skill now points at it as a
  pre-flight step before its closing `run` reminder.

### Changed

- **incognito:** a `DIRECT_ID` column now requires an explicit `directIdStrategy`. **Breaking.**
  Previously a `DIRECT_ID` with no declared strategy silently fell back to `ALTEREGO_GENERIC`
  (shape-preserving fabrication with no fictionality guarantee, ADR 21); it now fails closed at
  schema-discovery time instead, the same way an undeclared `SENSITIVE` `distinguishing` flag
  already does (ADR 16). `ALTEREGO_GENERIC` remains fully valid as an explicit choice;
  `UNIQUE_CANDIDATE_KEY` is unaffected. See `docs/adr/0029-declared-direct-id-strategy.md`.
- **incognito:** fail-closed schema validation now reports every issue across the whole schema in
  one run, not one table (or one issue) at a time. `SchemaDiscoveryStage` previously threw on the
  first table with a problem, and even within one table stopped at the first
  `SENSITIVE`/`DIRECT_ID`/`QUASI_ID` issue hit (only unclassified columns were already collected
  per-table). Every check is now accumulated across every table before a single `ConfigException`
  lists them all.
- **incognito:** the `ColumnRole` vocabulary is now documented in one reachable place,
  `docs/spec/incognito.md` §4.1: the nine usable roles (already there, in the role ->
  transformation table) plus the five `RESERVED (post-v1.0)` roles, previously described only in
  `ColumnRole`'s own Javadoc. Doc-only; no behaviour change.
- **incognito:** clarified that `GENERATED_COLUMN` does not cover `GENERATED ALWAYS AS IDENTITY`.
  PostgreSQL spells a computed column and an identity primary key with the same
  `GENERATED ALWAYS AS` prefix, easy to assume wrongly are the same thing; only the computed case
  (`IS_GENERATEDCOLUMN`) is `GENERATED_COLUMN`. An identity PK (`IS_AUTOINCREMENT`) is tracked
  separately and still requires a role, ordinarily `PRIMARY_KEY` - it was never actually excluded
  from classification, but nothing said so in one place. Doc-only; no behaviour change.
- **effigies:** `effigies`' own `jar` task now produces a normal thin jar (just effigies' own
  classes); the standalone-runnable fat jar is a separate `identigonJar` task, not an override of
  `jar`. Anyone invoking `:effigies:jar` directly for the old fat-jar behaviour needs
  `:effigies:identigonJar` instead. A plain top-level `./gradlew build` still produces
  `effigies/build/libs/identigon.jar` without any extra step. The fat jar is unchanged in content
  and filename otherwise, and is also published to GitHub Packages under the
  `org.identigon:effigies` coordinate with a `standalone` classifier (not its own separate GAV,
  which would duplicate incognito/alterego/SnakeYAML/the Postgres driver for anyone resolving it
  normally), alongside the GitHub Release copy above. See
  `docs/adr/0028-publish-effigies-runnable-jar.md` and
  `docs/adr/0030-standalone-jar-assembly-back-in-effigies.md`.
- **effigies:** `run` now validates a `persistent`/`reproducible` `IDENTIGON_SALT`'s length before
  opening either database connection. `AlterEgo`'s builder already rejected a too-short salt, but
  only once pipeline construction reached it - after both connections were open. `run` now checks
  it against the new `IncognitoPipeline.MIN_SALT_BYTES`, next to its existing
  missing-`IDENTIGON_SALT` check.
- **effigies:** `discover --help`/`scaffold --help`/`run --help` (or `-h`, anywhere in the
  subcommand's own args) now print that subcommand's usage and exit `0` - previously only bare
  `help`/`-h`/`--help` as the very first argument was recognised, so e.g. `discover --help` fell
  through to `DiscoverCommand`'s own parsing and printed the same usage line only as a side effect
  of missing `--source-url`/`--source-user` (`EXIT_USAGE`, not a deliberate help request). `main()`
  now constructs `System.out`/`System.err` with an explicit UTF-8 `PrintStream` instead of the
  JVM's platform default, which is not UTF-8 in a POSIX-locale environment (common in minimal
  containers/CI images) and previously rendered `§` - used throughout SPEC-referencing error
  messages - as `?` there.
- **effigies:** Javadoc doclint (`Xdoclint:all`/`Xwerror`) now applies to every subproject
  unconditionally, not just wherever a javadoc jar happens to be published (alterego and
  incognito, previously - `withJavadocJar()` was standing in for "has a public API worth holding
  to this standard", which isn't what it means; effigies has one too, despite shipping no javadoc
  jar of its own, being a CLI rather than a published library). `./gradlew javadoc` at the root is
  the one command that exercises this everywhere. Fixed the gaps this surfaced: `PolicyInferrer`
  (effigies) was `public` with no doc comments on any of it, the only effigies class not already
  following the package-private-unless-actually-an-entry-point convention every sibling command
  class already follows - narrowed to package-private (nothing outside the package used it) rather
  than documenting an API that was never meant to be one.
- **effigies:** `scaffold` no longer emits `autoInfer: false`. incognito's `autoInfer` key is gone
  (see above); writing it into every generated `policy.yaml` would have been actively misleading
  now that it means nothing.

### Fixed

- **effigies:** `scaffold`'s TODO comments now point somewhere an author can actually reach. "see
  the role vocabulary" named nothing that existed in `docs/`; the unclassified-column stub now
  points at `docs/spec/incognito.md` §4.1 (see incognito above), and the ambiguous-`DIRECT_ID`
  stub points at `DirectIdStrategy`'s own Javadoc instead, which is where the typed-generator
  choice actually lives.

### Removed

- **incognito:** `PolicyInferrer` and `AnonymisationPolicy.Builder.autoInfer(boolean)` are
  removed. **Breaking.** Both were `@Deprecated(forRemoval = true)` since inference migrated to
  `effigies`' own `PolicyInferrer` (ADR 23); this was always going to be incognito's next major
  version (ADR 24), and it lands here. `AnonymisationPolicy.autoInfer` (the record component) is
  gone too - nothing else in the engine ever read it. `YamlPolicyParser` now silently ignores a
  leftover `autoInfer:` key in an old `policy.yaml` rather than acting on it, the same as any other
  key it doesn't recognise - it no longer means anything, but a stale key in an otherwise-valid
  file shouldn't fail the parse. The unclassified-column fail-closed message no longer carries an
  auto-infer hint inline; it now points at effigies' `scaffold`/`validate` commands instead, which
  is where a suggestion actually comes from.

## [1.1.0] - 2026-08-26

### Added

- **incognito:** `DirectIdStrategy.ALTEREGO_NINO`, `.ALTEREGO_NHS_NUMBER`,
  `.ALTEREGO_PASSPORT_NUMBER`, and `.ALTEREGO_DRIVING_LICENCE_NUMBER` added, wiring all four
  remaining `alterego` GB identifier generators (already present, unwired - ADR 0012) through to
  `TableTransformLoadStage` (fabrication), `AnonymisationReportBuilder` (illustrative DPIA
  samples), and `VerificationStage` (new fictionality checks: every fabricated value must carry
  its guaranteed-fictional prefix - `QQ` / `999` / `ZZ` / `99999` respectively - alongside the
  existing email/postcode/domain/URL checks). Each covered by its own live E2E test against a real
  Postgres. `creditCardNumber()` is the one identifier builtin still unwired - deliberately, since
  a card number is `SENSITIVE` (confirmed, same treatment as a bank account), not a plain
  `DIRECT_ID` - see the `redactionConstant` entry below for how that gap actually closed.
- **incognito:** `ColumnPolicy.redactionConstant` added - an optional caller-chosen fixed
  placeholder for a `RedactionStrategy.CONSTANT` column (e.g. `"0000 0000 0000 0000"` for a card
  number), instead of the generic `"REDACTED"` every `CONSTANT`-redacted text column got before.
  Text-type columns only; a non-text column with one set fails closed with a clear
  `ConfigException` at pipeline-build time, not per row (SPEC §7.2). Wired through
  `YamlPolicyParser` and the DPIA report's illustrative samples too. This is the closure for the
  credit-card-number gap above - redact to one obviously-fake constant, rather than fabricating a
  typed per-row value - and is general-purpose beyond credit cards (any `SENSITIVE
distinguishing: true` text column wanting a specific placeholder).
- **incognito:** `TableTransformLoadStage` now opens one `alterego` `RecordScope` per source row,
  keyed on the row's own source PK (deterministic, reproducible-mode-safe), and routes every
  `DIRECT_ID`/`UNIQUE_CANDIDATE_KEY` typed generator through it instead of calling the
  transformation bare. A no-op for every strategy except `ALTEREGO_CITY`/`ALTEREGO_POSTCODE`/
  `ALTEREGO_PHONE` - the only three that ever consult record-scoped attributes - so a table
  classifying two or more of those three now fabricates them coherently: same fictional UK region
  within a row, never independently-picked unrelated parts of the country. Verified with a new
  `RecordCoherenceE2ETest` against a real Postgres, reusing the same area-to-dialling-code mapping
  `alterego`'s own `RecordCoherenceIntegrationTest` treats as ground truth. `SPECIFICATION.md`
  §4.1/Appendix A updated.
- **incognito:** PMD added to the build, sharing a root `config/pmd/ruleset.xml` with
  `alterego`/`effigies`.
- **incognito:** JaCoCo added to the build, sharing the root `subprojects { }` block's
  report/`check`-task config with alterego/effigies (previously alterego-only; see the alterego
  entry below).
- **effigies:** added a `quickstart/` worked example (originally `effigies/examples/quickstart/`;
  moved to the repository root in a later restructuring) - a small first-party PostgreSQL schema
  (`customers`/`orders`/`support_tickets`, no third-party data, no Docker/Testcontainers
  dependency) with a hand-authored `policy.yaml` and a step-by-step README, so evaluating the
  `discover` -> `scaffold` -> `run` workflow no longer requires a real production database or one
  of incognito's Docker-gated benchmark fixtures. Linked from the main README's new "Try it in
  five minutes" section. Demonstrates every `DirectIdStrategy`/`QuasiIdStrategy` family in one
  schema, including the new `ALTEREGO_NINO` (see the incognito entry above) and, deliberately, the
  `ALTEREGO_GENERIC` fallback for a bank-account column with no typed generator yet.
- **effigies:** added `quickstart/run-quickstart.sh` (POSIX `sh`) and `run-quickstart.ps1`
  (PowerShell) - twin, behaviourally-identical scripts; Docker + Java 25 only, nothing else to
  install. `run-quickstart` (no args) is a one-shot demo: starts a throwaway Postgres container,
  loads the schema and sample data, builds the CLI jar if needed, runs `discover` -> `scaffold` ->
  `run` against the finished `policy.yaml`, and prints the fabricated rows plus the DPIA report
  location. `setup` / `run` instead exercises the real authoring workflow: `setup` stops after
  `scaffold`, leaving a draft for the `identigon-policy-author` Agent Skill (or a human) to
  classify by hand, and `run` reuses the same container and anonymises against whatever policy
  results - failing closed with a clear error if any column is still unclassified (see the
  incognito `YamlPolicyParser` fix below - this is the workflow that found it). `clean` tears down
  the throwaway container and any generated files either way. The manual step-by-step walkthrough
  in the example's README is unchanged, for anyone who wants to run or understand each step
  without a script.
- **incognito:** `PolicyInferrer` gains heuristics for postcodes (`QUASI_ID`), passport numbers,
  driving licence numbers, and credit card numbers (`DIRECT_ID`), and anchors the email/phone
  patterns to the end of the column name so a boolean like `email_verified` no longer gets
  suggested as DIRECT_ID.
- **effigies:** added tests for `RunCommand`, `DiscoverCommand`, `PolicyInferrer`, and
  `SimpleDataSource` (previously untested), splitting the CLI commands into a directly-testable
  core to do it without needing to fake environment variables.
- **effigies:** PMD added to the build, sharing a root `config/pmd/ruleset.xml` with
  `alterego`/`incognito`. No behavioural change here - only a `StringBuilder` under-sized for what
  it accumulates.
- **effigies:** JaCoCo added to the build, sharing the root `subprojects { }` block's
  report/`check`-task config with alterego/incognito (previously alterego-only; see the alterego
  entry below). Javadoc/doclint enforcement stays deliberately absent here - effigies is a CLI,
  not a published library.
- markdown line-length lint added, then the full default `markdownlint-cli2` rule set enabled.
  `.markdownlint-cli2.jsonc` runs `markdownlint-cli2` as a pre-commit hook. It started
  `MD013`-only (100-column line length; code blocks, tables, and headings exempt), with existing
  violations fixed in the same change. Every other default rule was then audited and turned on:
  `MD013` gained `stern` mode (forgives a bare long token alone on its own line - typically a URL
  - but not one sharing a line with prose); `MD024` (no-duplicate-heading) uses `siblings_only` so
    Keep a Changelog's repeated `### Added`/`### Fixed` headings per version stay legal; the rest
    (`MD004`, `MD007`, `MD012`, `MD022`, `MD031`, `MD032`, `MD034`, `MD036`, `MD038`, `MD040`,
    `MD060`, ...) needed no config, just fixing the content across the repo's markdown files.
- Dependabot added (`gradle` + `github-actions`, weekly) to keep dependency versions current
  across the monorepo.
- CI reports made downloadable. `_build.yml` uploads each matrix leg's (`ubuntu-latest`/
  `windows-latest`) JUnit XML, JaCoCo (HTML/XML/CSV), PMD (HTML/XML), and SpotBugs (HTML) reports
  as a `build-reports-<os>` artifact via `actions/upload-artifact`, even when the build step fails
  - previously these only existed buried in the raw Gradle log.
- Gradle version catalog added (`gradle/libs.versions.toml`). Every shared version - root plugin
  versions, SpotBugs/PMD `toolVersion`s, find-sec-bugs, the JUnit BOM, snakeyaml, H2, the
  Testcontainers BOM, the Postgres driver - was a literal string repeated in one or more
  `build.gradle.kts` files; all ten now have exactly one declaration. No version actually changed
  (verified: the published `incognito` POM still resolves `snakeyaml` to `2.2`, matching before).
  CI's downloadable per-run artifact (see above) makes this easy to spot-check going forward: the
  JaCoCo/PMD/SpotBugs reports inside it reflect whatever the catalog resolved.

### Changed

- **alterego:** Spotless/SpotBugs/PMD build config consolidated to the monorepo root.
  `config/spotbugs/exclude.xml` moved to the shared root `config/spotbugs/exclude-alterego.xml`
  (alongside `config/pmd/ruleset.xml`); the identical Spotless/SpotBugs/PMD settings that were
  copy-pasted into each subproject's `build.gradle.kts` are now declared once in the root's
  `subprojects { }` block. Only the SpotBugs `excludeFilter` path - genuinely different per
  subproject - stays local. One user-visible build change: the SpotBugs XML report is no longer
  produced here (only HTML), matching incognito/effigies; nothing was consuming it. The
  find-sec-bugs plugin dependency (identical in all three `dependencies { }` blocks) moved into
  the same root config. JUnit BOM aligned to `6.1.3` across all three subprojects (was `5.11.4`
  here, `5.10.2` in incognito/effigies).
- **alterego:** JaCoCo and Javadoc/doclint config also elevated to the root `subprojects { }`
  block. JaCoCo's report shape (`xml`/`html`/`csv`) and `check`-task wiring moved out of this
  subproject's own `build.gradle.kts` into the shared block (`plugins.withId("jacoco")`);
  alterego now applies only `id("jacoco")` locally. Doclint's `Xdoclint:all`/`Xwerror` similarly
  moved into a `plugins.withId("maven-publish")` guard, which correctly reaches
  alterego/incognito (both publish a javadoc jar) and skips effigies (a CLI, deliberately not
  published) without a bespoke flag. No behavioural change to alterego's own reports or doclint
  enforcement.
- **incognito:** Spotless/SpotBugs config consolidated to the monorepo root alongside the PMD move
  above - `config/spotbugs/exclude-incognito.xml`; see the alterego entry above for the mechanics,
  including the find-sec-bugs and JUnit BOM alignment.
- **incognito:** identifier quoting fixed in both dialect handlers. `PostgresDialectHandler`
  (`buildInsertSql`, `preLoadTable`'s owner-mode fallback, `postLoadTable`, `resyncSequence`) and
  `GenericDialectHandler.buildInsertSql` now quote every raw table/column identifier - previously
  only the FK drop/recreate path did. A reserved-word or mixed-case table/column name broke
  inconsistently depending on which code path touched it; none of the benchmark fixtures happen to
  use such names, so this was silent until now.
- **effigies:** `ScaffoldCommand` writes its output as UTF-8 explicitly, not the platform-default
  charset (not UTF-8 on Windows) a bare `FileWriter` used.
- **effigies:** CLI error paths report the exception itself, not just its (often empty) message.
- **effigies:** Spotless/SpotBugs config consolidated to the monorepo root -
  `config/spotbugs/exclude-effigies.xml`; see the alterego entry above for the mechanics,
  including the find-sec-bugs and JUnit BOM alignment.
- **effigies:** the runnable jar is now `identigon.jar`, not `effigies.jar`. Consumers only ever
  run this one artifact (alterego/incognito stay internal, sibling-project dependencies), so the
  jar name, the `--version`/`--help`/usage banner text, and the manifest `Implementation-Title`
  now say "Identigon" instead of "Effigies". The Gradle module, its `org.identigon.effigies`
  package, and the `EffigiesCli` class name are unchanged - this is the public artifact name only,
  not a module rename.

### Deprecated

- **incognito:** `PolicyInferrer` and `AnonymisationPolicy.Builder.autoInfer(boolean)` are now
  `@Deprecated(forRemoval = true)`. Inference is authoring, not execution - the maintained version
  has lived in `effigies`' own `PolicyInferrer` since the 1.0.0 split - and this copy is scheduled
  for removal at incognito's next major version (see
  `docs/adr/0023-authoring-above-the-engine.md`). No behavioural change yet; this is the
  deprecation notice ahead of that removal.

### Fixed

- **incognito:** fixed what PMD found (see PMD added, above): a
  `SchemaDiscoveryStage.validateTablePolicy` parameter that never actually gated anything (the
  fail-closed suggestion hint was always included, regardless of `autoInfer`);
  `DefaultIncognitoPipeline`'s catch split into `IncognitoException`/`Exception` branches instead
  of an `instanceof` check; the in-memory stores declare `Map` fields instead of
  `ConcurrentHashMap`; a couple of dead/redundant bits of code (an always-overwritten initializer,
  a no-op catch-and-rethrow). No behavioural change.
- **incognito:** `YamlPolicyParser` no longer crashes on a `scaffold`-shaped draft policy. Every
  `role:`/`surrogateStrategy:`/`directIdStrategy:`/`quasiIdStrategy:`/`redactionStrategy:` key
  `scaffold` writes is present with a blank (YAML `null`) value, by design - `containsKey(...)` is
  true for those, so each was resolved via `EnumType.valueOf(String.valueOf(null).toUpperCase())`,
  i.e. `EnumType.valueOf("NULL")`, throwing an unhandled `IllegalArgumentException` instead of
  leaving the field unset for the existing fail-closed validation to report clearly. Found running
  the effigies quickstart's new `setup`/`run` workflow (see above) against a real database for the
  first time - previously masked because live testing had always used a fully-classified policy.
  Now checks the value itself (`!= null`), not just key presence; the fail-closed error is once
  again the clear, column-by-column message `scaffold`'s own output promises.
- **effigies:** fixed - `identigon.jar` could not connect to a real PostgreSQL database at all.
  effigies never declared a runtime dependency on the PostgreSQL JDBC driver - incognito is
  deliberately driver-agnostic (works against any caller-supplied `DataSource`) and only pulls the
  driver in `testRuntimeOnly` scope for its own Testcontainers tests, so nothing in the dependency
  graph ever put `org.postgresql.Driver` on the CLI's own runtime classpath, despite the
  jar-merging task's own comment claiming "JDBC drivers" were bundled. `SimpleDataSource`'s
  `DriverManager.getConnection(...)` therefore always threw `SQLException: No suitable driver
found`, surfaced to the user as an opaque `Failed to inspect schema` with the real cause
  swallowed. Every documented `java -jar build/libs/identigon.jar discover/scaffold/run` example -
  in this repo's own READMEs and the public Getting Started guide - was unusable exactly as
  written. Found running the effigies quickstart's `run-quickstart.sh`/`.ps1` (see above) against a
  real database for the first time; `runtimeOnly(libs.postgresql)` added to
  `effigies/build.gradle.kts` fixes it.
- **effigies:** two portability bugs found while exercising the new `run-quickstart` scripts (see
  above) end to end - all four commands, both fresh-container and container-reuse paths, and the
  fail-closed path - against a real Docker Desktop + PostgreSQL, neither Bash/PowerShell
  version-specific: the readiness check could pass against the official Postgres image's brief
  _temporary_ startup instance (for `initdb`) moments before it restarts for the real listener, a
  narrow window where a query could hit the socket mid-restart - now requires two consecutive
  successful `pg_isready` checks, not one. Windows-only wrinkles, one per script: the `sh` version
  now avoids `docker inspect -f '{{...}}'` (MSYS2/Git-Bash mangles `{{ }}` template arguments to
  native Windows executables) in favour of a template-free `docker ps -q -f name=...` check, and
  uses `printf '%s\n'` instead of `echo` for any value that might contain backslashes - POSIX
  `echo` is free to interpret them as escapes, and every Windows path has some; the `.ps1` version
  avoids naming a parameter `$Args` (PowerShell's own reserved automatic-variable name), which
  silently breaks splatting it onward to a wrapped command.

## [alterego-0.1.0] - 2026-07-26

Initial implementation, milestones M0-M6 of `alterego/PLAN.md` (now deleted; see git history).

### Added

- Deterministic pseudonymisation core: per-input HMAC-SHA256 key derivation, counter-mode
  randomness stream, sampling primitives (Appendix A), frozen conformance vectors.
- Pattern-based (`pattern()`), constant (`constant()`), and masking (`mask()`) transformations.
- Name and address built-ins: `firstName()`, `lastName()`, `fullName()`, `city()`,
  `streetAddress()`, `postcode()`, `organisationName()`, backed by curated, provenance-tracked UK
  dictionaries (`docs/research/0001-alterego-dictionaries.md`). `lastName()` and
  `streetAddress()`'s theme words
  go further: authored, deliberately fictional vocabulary rather than real data, so a pseudonymised
  name or street address reads as unmistakably fictional, not merely a real one attached to the
  wrong person (ADR 0010).
- Temporal jitter: `shiftDate(...)`/`shiftDateTime(...)`, sixteen methods across eight jitter
  strategies, with inclusive `JitterOptions` clamping.
- Fictional-by-default contact details: `emailAddress()` (RFC 2606 reserved domains) and
  `phoneNumber()` (Ofcom drama ranges, `docs/research/0003-alterego-phone-ranges.md`), each with a
  `realistic()`
  opt-out.
- `MappingStore` SPI, `InMemoryMappingStore`, and the `stored()`/`unique()` decorators, with the
  full section 2.5 decorator algebra and a reusable store contract test.
- Record coherence: `RecordScope` (anonymous and keyed), `RecordAttributes`, and built-in coherence
  between `city()`/`postcode()`/`phoneNumber()` via `UK_POSTCODE_AREA`/`UK_NATION` - whichever of
  the three runs first in a scope establishes the record's place for the others to follow.
- Extensibility: any `Strategy<T>` lambda bound via `AlterEgo.bind(...)` gets full built-in parity
  (determinism, `unique()`, `stored()`, record coherence, `derived(...)` composition).
- `maven-publish` configuration (group `org.identigon`, artifact `alterego`).

## [alterego-0.2.0] - 2026-07-31

### Added

- `FileMappingStore` (spec §5.4): a persistent, file-backed `MappingStore` that provides cross-run
  stability for `stored()` and `unique()` mappings.
- Five identifier built-ins (`nhsNumber()`, `nationalInsuranceNumber()`, `drivingLicenceNumber()`,
  `passportNumber()`, `creditCardNumber()`) with structurally guaranteed fictional outputs (ADR
  0012).

## [alterego-0.3.0] - 2026-08-02

### Added

- `domainName()` and `url()` built-ins using RFC 2606 reserved domains.
- `shiftInstant()` family to `AlterEgo` for `Instant` jitter.
- `PhoneOptions.includeNonGeographic()` to allow `phoneNumber()` to draw from Ofcom freephone,
  premium rate, and UK-wide drama ranges.
- Supported `LocalTime`, `YearMonth`, and `BigDecimal` in the canonical value codecs and `redact()`.

## [incognito-1.0.0] - 2026-08-02

Pre-1.0 development. v1.0 scope: PostgreSQL only; in-memory key/cascade stores; single-threaded.

### Added

- **Pipeline & policy engine** (Phases 1-3): `IncognitoPipeline` builder with auto-assembled default
  stages; `AnonymisationPolicy` / `TablePolicy` / `ColumnPolicy` records and builders; a
  programmatic and YAML (`YamlPolicyParser`) policy surface; `SchemaInspector` (tables, PKs, FKs,
  unique candidate keys, identity vs generated columns); `TableDependencyGraph` topological
  ordering; fail-closed classification with advisory `PolicyInferrer` suggestions.
- **Fabrication engine** (Phase 4): streaming transform+load; `DIRECT_ID` / `UNIQUE_CANDIDATE_KEY`
  via `alterego` with a length-preserving collision fallback; `QUASI_ID` temporal jitter,
  including one salt-keyed delta per coherence group inherited by descendants (ADR 18); declared
  `distinguishing` handling for `SENSITIVE` columns (ADR 16); root-ancestor `INHERITED_ATTRIBUTE`
  resolution (ADR 20); primary-key surrogates and foreign-key rewriting.
- **Key & cascade stores** (Phase 5): `InMemoryKeyTranslationStore` and
  `InMemoryAttributeCascadeStore` (published attributes, FK linkage, and group-scoped jitter
  deltas). Single-column **and** composite (`CompositeKey`) keys.
- **Loader, cyclic FKs, clean-up & verification** (Phase 6): `PostgresDialectHandler` (+ generic
  ANSI fallback) with `session_replication_role` trigger isolation, `OVERRIDING SYSTEM VALUE`, and
  sequence resync; cyclic / self-referential foreign keys via Tarjan SCC plus a placeholder and a
  second-pass `UPDATE` (ADR 19); `IncognitoCleanUpHandler` compensation on failure with salt
  destruction; `VerificationStage` (referential integrity, e-mail fictionality, per-period volume
  tolerances, the default-on misdeclaration lint, and a source-value survival net);
  `AnonymisationReportBuilder` (with the §7.2 opaque-type passthrough audit) and a
  `DpiaArtefactEmitter` that writes JSON, HTML, or Markdown.
- **alterego 0.3.0 adoption:** **type-aware redaction** - `CONSTANT`/`MASK` now delegate to
  `AlterEgo.redact(Class<T>)`/`constant`/`mask`, so numeric, temporal, boolean and opaque
  `SENSITIVE`
  columns get a type-appropriate constant instead of failing at insert; **salt destruction** - the
  `AlterEgo` instance's internal salt clone is now zeroed on completion via `AlterEgo.close()`, not
  just Incognito's own copy; and **`TIMESTAMP`/`LocalDateTime` quasi-identifier `SYNTHESISE`** (a
  timestamp DOB is shifted within the salt-keyed ±5y window, preserving type and time-of-day).
- **Observability** via the JDK `System.Logger` facade (zero-dependency): previously-swallowed
  best-effort compensation failures now log a `WARNING`, and benign fallbacks (owner-mode trigger
  handling, pg_stats-unavailable) log at `DEBUG`. Each record carries only the operation, table and
  SQLState - never the salt, a field value, or the raw exception message (§7.3/§5.1).
- **Owner-mode cyclic-FK load** (SPEC §9): a non-superuser target that _owns_ its tables now clones
  cyclic/self-referential FKs by dropping the cyclic FK constraints for the load and recreating them
  verbatim (`pg_get_constraintdef`) after the pass-2 `UPDATE` - where a superuser uses
  `session_replication_role`. The drop/recreate is atomic (transactional DDL) and is recreated on
  failure too; a role that can do neither still fails fast.

### Fixed

- **Enum / user-type passthrough.** A kept (`PAYLOAD`) column of a PostgreSQL `enum` or other
  user-defined type failed on re-insert - the read `String` was bound as `varchar`, so any table
  with an enum column (e.g. Pagila's `film.rating`) could not be cloned. `String` values now bind as
  `Types.OTHER` so PostgreSQL casts them to the column's actual type (enum, `tsvector`, `uuid`,
  ...).
- `JITTER_DAYS` no longer raises spurious per-period volume-drift _warnings_: because a ±N-day
  jitter crosses month boundaries, the verification volume check now buckets it **yearly** (not
  monthly), where a day-window barely leaks. Cosmetic - it never failed the run.

**Note:** incognito's independently-versioned history as a standalone repository continued to
`1.1.0` after this release, before rejoining this monorepo's lockstep numbering at `1.0.0` (see the
lockstep-versioning ADR) - that `1.1.0` release's own changes were never recorded in a
`CHANGELOG.md` entry before the repositories merged, and are not reconstructed here.

## [alterego-0.4.0] - 2026-08-09

### Added

- Guarded Maven Central artifact signing in the `maven-publish` configuration: active only when a
  `SIGNING_KEY` is supplied, so ordinary `build` runs are unaffected.

### Fixed

- `drivingLicenceNumber()` now formats its digits with `Locale.ROOT`, so output no longer depends on
  the JVM's default formatting locale. Under a default locale with a non-Latin numbering system the
  month/day digits could previously render as non-ASCII glyphs; reference-salt output (ASCII) is
  unchanged.
- `FileMappingStore` now re-establishes its header when recovery truncates the file to empty. A
  crash while writing the initial header line previously left the store with no header, making it
  permanently unopenable on the next `open()`.
- `shiftInstant()` now validates `days`/`seconds >= 0` at configuration time, throwing
  `AlterEgoConfigException` like `shiftDate()`/`shiftDateTime()`, instead of throwing a raw
  `IllegalArgumentException` lazily on first application.

## [effigies-1.0.0] - 2026-08-09

### Added

- Phase 5 Agent Skill: Added `identigon-policy-author` Agent Skill
  (`.agents/skills/identigon-policy-author/SKILL.md`) to conduct interactive, fail-closed user
  interviews for policy classification, featuring paginated topology parsing and aggressive
  batching to mitigate fatigue.
- Phase 4 Orchestration: Added `run` subcommand to execute `incognito` using a finished
  `policy.yaml`, supporting ephemeral, persistent, and reproducible salt modes. Surfaces the
  engine's DPIA accountability report via `DpiaArtefactEmitter` as `dpia-report.html`,
  `dpia-report.json`, and `dpia-report.md`.
- Phase 3 Inference: Added `PolicyInferrer` to auto-suggest column roles based on naming
  heuristics during `scaffold`. Suggestions are emitted strictly as YAML comments to preserve
  fail-closed execution.
- `discover` subcommand: Inspects a source database using `incognito`'s `SchemaInspector`
  and prints a metadata-only summary.
- `scaffold` subcommand: Emits a fail-closed starter `policy.yaml` with schema metadata as comments.
- Project skeleton: Gradle (Kotlin DSL) build with a Java 25 toolchain and a single runnable jar
  (`java -jar`); Spotless + SpotBugs/find-sec-bugs; pre-commit hooks + gitleaks; CI mirroring the
  sibling repos (a reusable `_build.yml` invoked by `main.yml` and `pull-request.yml`) that resolves
  incognito and alterego from GitHub Packages.
- `EffigiesCli` dispatch skeleton (`discover` / `scaffold` / `run` declared; `help` / `version`
  live), covered by `EffigiesCliTest`.
- Base docs: `README.md`, `SPECIFICATION.md`, `PLAN.md`, `docs/adr/` (ADR 23 - authoring above the
  engine), and an initial `docs/tasks/` handoff for schema discovery + scaffold.

## [1.0.0] - 2026-08-10

### Added

- **incognito:** `DirectIdStrategy`: `ALTEREGO_POSTCODE`, `ALTEREGO_DOMAIN`, `ALTEREGO_URL`. Three
  previously unexposed `alterego` typed generators (`postcode()`, `domainName()`, `url()`) are now
  reachable from policy. `VerificationStage` positively asserts each strategy's fictionality
  guarantee on the target (GB postcode inward-code letter; RFC 2606 reserved domain/TLD for
  domain/URL), and these strategies are excluded from the generic DIRECT_ID survival check, same as
  `ALTEREGO_EMAIL`.

### Changed

- **alterego:** merged into the `identigon` monorepo alongside `incognito` and `effigies`, each a
  Gradle subproject with full history preserved. Versioning is now lockstep across all three,
  sourced from the monorepo root (see `docs/adr/0024-lockstep-versioning.md`). A deliberate
  re-baseline to 1.0.0, not a claim that four minor versions' worth of API changes happened at
  once - alterego's own history before this point is the `alterego-0.1.0`-`0.4.0` entries above.
- **incognito:** merged into the `identigon` monorepo alongside `alterego` and `effigies`, each a
  Gradle subproject with full history preserved. Versioning is now lockstep across all three,
  sourced from the monorepo root (see `docs/adr/0024-lockstep-versioning.md`). Moves backward in
  number from incognito's last independent release, `1.1.0` -> `1.0.0` - deliberate, not a
  downgrade; see the ADR.
- **effigies:** merged into the `identigon` monorepo alongside `alterego` and `incognito`, each a
  Gradle subproject with full history preserved. Versioning is now lockstep across all three,
  sourced from the monorepo root (see `docs/adr/0024-lockstep-versioning.md`). The version number
  itself is unchanged from effigies' prior standalone release - both are 1.0.0 - this entry exists
  so that change in what "1.0.0" means isn't silently missing from the record.
