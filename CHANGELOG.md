# Changelog

Release notes for the `identigon` monorepo as a whole. `alterego`, `incognito`, and `effigies`
version together (lockstep - see any subproject's "lockstep versioning" ADR): one version number,
one tag, one entry here per release, with a subsection per subproject that actually changed that
release (a subproject with nothing to report that release has no subsection).

Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) - versioned,
dated, human-curated entries - adapted for a monorepo: each version groups changes by subproject
rather than by change type.

Every release before 1.0.0 (when the three subprojects joined lockstep) is folded in below with a
project-prefixed version tag (`alterego-0.1.0`, `incognito-1.0.0`, `effigies-1.0.0`, ...) instead
of a bare number, since each subproject's own pre-lockstep numbering restarted independently and
would otherwise collide with this file's own `1.0.0` (the first *shared* release) - most visibly,
`incognito` and `effigies` each had their own unrelated `1.0.0` before joining lockstep. Each
subproject's standing output-stability guarantees / hard invariants, previously repeated at the top
of its own now-retired `CHANGELOG.md`, live in its `docs/spec/` member instead - restating them here
too would be the same fact in two places.

## [Unreleased]

## [2.0.0] - 2026-09-01

### alterego

- **Now also published as a `GitHubPackages`-mirrored jar attached to the GitHub Release, once a
  release is cut via the new `.github/workflows/release.yml`.** No change to the Maven artifact
  itself (`org.identigon:alterego`); this only adds a second, token-free way to fetch the plain
  jar directly (see effigies below for why one was needed).

### incognito

- Same GitHub Release mirroring as alterego, above - no change to `org.identigon:incognito` itself.
- **A `DIRECT_ID` column now requires an explicit `directIdStrategy`.** Previously a `DIRECT_ID`
  with no declared strategy silently fell back to `ALTEREGO_GENERIC` (shape-preserving
  fabrication with no fictionality guarantee, ADR 21); it now fails closed at schema-discovery
  time instead, the same way an undeclared `SENSITIVE` `distinguishing` flag already does (ADR
  16). `ALTEREGO_GENERIC` remains fully valid as an explicit choice; `UNIQUE_CANDIDATE_KEY` is
  unaffected. See `docs/adr/0029-declared-direct-id-strategy.md`.
- **Fail-closed schema validation now reports every issue across the whole schema in one run,
  not one table (or one issue) at a time.** `SchemaDiscoveryStage` previously threw on the first
  table with a problem, and even within one table stopped at the first `SENSITIVE`/`DIRECT_ID`/
  `QUASI_ID` issue hit (only unclassified columns were already collected per-table). Every check
  is now accumulated across every table before a single `ConfigException` lists them all.
- **New public constant `IncognitoPipeline.MIN_SALT_BYTES`**, re-exported from `AlterEgo`'s own
  minimum-salt-length requirement, so a caller can validate a `persistentSalt`/`reproducible` salt
  up front instead of waiting for `build()` to reject it (see effigies below for the first caller).
- **The `ColumnRole` vocabulary is now documented in one reachable place**, `docs/spec/incognito.md`
  §4.1: the nine usable roles (already there, in the role -> transformation table) plus the five
  `RESERVED (post-v1.0)` roles, previously described only in `ColumnRole`'s own Javadoc.
- **Clarified that `GENERATED_COLUMN` does not cover `GENERATED ALWAYS AS IDENTITY`.** PostgreSQL
  spells a computed column and an identity primary key with the same `GENERATED ALWAYS AS` prefix,
  easy to assume wrongly are the same thing; only the computed case (`IS_GENERATEDCOLUMN`) is
  `GENERATED_COLUMN`. An identity PK (`IS_AUTOINCREMENT`) is tracked separately and still requires a
  role, ordinarily `PRIMARY_KEY` - it was never actually excluded from classification, but nothing
  said so in one place. Doc-only; no behaviour change.
- **`SchemaDiscoveryStage.validate(List<TableMetadata>, AnonymisationPolicy)` is now public.** The
  same fail-closed check `process` already ran, pulled out so it is callable directly against an
  already-inspected schema - no target connection, no `PipelineContext`, no dependency-graph
  computation. `process` now calls it internally; no behaviour change there. See effigies' new
  `validate` command below for the first caller.
- **BREAKING: `PolicyInferrer` and `AnonymisationPolicy.Builder.autoInfer(boolean)` are removed.**
  Both were `@Deprecated(forRemoval = true)` since inference migrated to `effigies`' own
  `PolicyInferrer` (ADR 23); this was always going to be incognito's next major version (ADR 24),
  and it lands here. `AnonymisationPolicy.autoInfer` (the record component) is gone too - nothing
  else in the engine ever read it. `YamlPolicyParser` now silently ignores a leftover `autoInfer:`
  key in an old `policy.yaml` rather than acting on it, the same as any other key it doesn't
  recognise - it no longer means anything, but a stale key in an otherwise-valid file shouldn't
  fail the parse. The unclassified-column fail-closed message no longer carries an auto-infer hint
  inline; it now points at effigies' `scaffold`/`validate` commands instead, which is where a
  suggestion actually comes from.

### effigies

- **`effigies` is now published to GitHub Packages too** (`org.identigon:effigies`), the same way
  alterego/incognito already are - a normal thin jar with an accurate POM (no javadoc/sources jar).
  Previously effigies had no `publishing {}` block at all.
- **The standalone-runnable fat jar moved from `effigies/build/libs/identigon.jar` to
  `build/libs/identigon.jar` (the monorepo root), and is now a dedicated `identigonJar` task
  there instead of an override of effigies' own `jar` task.** `effigies/build.gradle.kts`'s `jar`
  task now produces a normal thin jar (just effigies' own classes) - anyone invoking `:effigies
  :jar` directly for the old fat-jar behaviour needs the root-level `identigonJar` task instead.
  A plain top-level `./gradlew build` still produces `identigon.jar` without any extra step. The
  fat jar is unchanged in content and filename otherwise, and is also published to GitHub Packages
  under the `org.identigon:effigies` coordinate with a `standalone` classifier, alongside the GitHub
  Release copy above. See `docs/adr/0028-publish-effigies-runnable-jar.md`.
- **`scaffold` now suggests `directIdStrategy`, `distinguishing`, `references` and
  `surrogateStrategy`, not just `role`.** Previously only `role:` carried a suggestion comment,
  leaving every other decision that determines output quality to be hand-written from scratch. Now:
  a `DIRECT_ID` suggestion with an unambiguous heuristic also suggests the matching
  `directIdStrategy`; a `SENSITIVE` suggestion suggests filling in `distinguishing`; a column
  `SchemaInspector` already knows is structurally a primary or foreign key is suggested as such
  (a fact, not a heuristic guess) with `surrogateStrategy`/a pre-filled
  `references: {table, column}` respectively. Still "suggest, never assign" throughout - every
  suggestion is a comment, nothing is written into a real key. Also fixes `PolicyInferrer`'s
  `CREDIT_CARD_PATTERN`: it suggested
  `DIRECT_ID`, but a card number has no typed fictional-generator and is conventionally redacted -
  it now suggests `SENSITIVE`, matching how `incognito` actually treats one.
- **`run` now validates a `persistent`/`reproducible` `IDENTIGON_SALT`'s length before opening
  either database connection.** `AlterEgo`'s builder already rejected a too-short salt, but only
  once pipeline construction reached it - after both connections were open. `run` now checks it
  against the new `IncognitoPipeline.MIN_SALT_BYTES`, next to its existing missing-`IDENTIGON_SALT`
  check.
- **`scaffold`'s TODO comments now point somewhere an author can actually reach.** "see the role
  vocabulary" named nothing that existed in `docs/`; the unclassified-column stub now points at
  `docs/spec/incognito.md` §4.1 (see incognito above), and the ambiguous-`DIRECT_ID` stub points at
  `DirectIdStrategy`'s own Javadoc instead, which is where the typed-generator choice actually
  lives.
- **Three CLI ergonomics fixes.** `discover --help`/`scaffold --help`/`run --help` (or `-h`,
  anywhere in the subcommand's own args) now print that subcommand's usage and exit `0` -
  previously only bare `help`/`-h`/`--help` as the very first argument was recognised, so e.g.
  `discover --help` fell through to `DiscoverCommand`'s own parsing and printed the same usage
  line only as a side effect of missing `--source-url`/`--source-user` (`EXIT_USAGE`, not a
  deliberate help request).
  `scaffold` gained `--force` to overwrite an existing output file (previously always refused, so
  re-scaffolding after a schema change needed a manual `rm` first). `main()` now constructs
  `System.out`/`System.err` with an explicit UTF-8 `PrintStream` instead of the JVM's platform
  default, which is not UTF-8 in a POSIX-locale environment (common in minimal containers/CI images)
  and previously rendered `§` - used throughout SPEC-referencing error messages - as `?` there.
- **New `validate` command.** `SchemaDiscoveryStage`'s fail-closed messages were the tool's best
  diagnostics but only reachable by committing to a full `run`. `validate --policy ./policy.yaml
  --source-url ... --source-user ...` checks a policy against the source schema with no target
  connection and no data movement - the same errors `run` would raise, cheaper to iterate against
  while authoring, and usable as a CI pre-flight check for a policy going stale after a schema
  migration. The `identigon-policy-author` Agent Skill now points at it as a pre-flight step before
  its closing `run` reminder.
- **Javadoc doclint (`Xdoclint:all`/`Xwerror`) now applies to every subproject unconditionally**,
  not just wherever a javadoc jar happens to be published (alterego and incognito, previously -
  `withJavadocJar()` was standing in for "has a public API worth holding to this standard", which
  isn't what it means; effigies has one too, despite shipping no javadoc jar of its own, being a
  CLI rather than a published library). `./gradlew javadoc` at the root is the one command that
  exercises this everywhere. Fixed the gaps this surfaced: `PolicyInferrer` (effigies) was `public`
  with no doc comments on any of it, the only effigies class not already following the
  package-private-unless-actually-an-entry-point convention every sibling command class already
  follows - narrowed to package-private (nothing outside the package used it) rather than
  documenting an API that was never meant to be one.
- **`scaffold` no longer emits `autoInfer: false`.** incognito's `autoInfer` key is gone (see
  incognito above); writing it into every generated `policy.yaml` would have been actively
  misleading now that it means nothing.

## [1.1.0] - 2026-08-26

### alterego

- **Spotless/SpotBugs/PMD build config consolidated to the monorepo root.**
  `config/spotbugs/exclude.xml` moved to the shared root `config/spotbugs/exclude-alterego.xml`
  (alongside `config/pmd/ruleset.xml`); the identical Spotless/SpotBugs/PMD settings that were
  copy-pasted into each subproject's `build.gradle.kts` are now declared once in the root's
  `subprojects { }` block. Only the SpotBugs `excludeFilter` path - genuinely different per
  subproject - stays local. One user-visible build change: the SpotBugs XML report is no longer
  produced here (only HTML), matching incognito/effigies; nothing was consuming it. The
  find-sec-bugs plugin dependency (identical in all three `dependencies { }` blocks) moved into the
  same root config. JUnit BOM aligned to `6.1.3` across all three subprojects (was `5.11.4` here,
  `5.10.2` in incognito/effigies).
- **JaCoCo and Javadoc/doclint config also elevated to the root `subprojects { }` block.** JaCoCo's
  report shape (`xml`/`html`/`csv`) and `check`-task wiring moved out of this subproject's own
  `build.gradle.kts` into the shared block (`plugins.withId("jacoco")`); alterego now applies only
  `id("jacoco")` locally. Doclint's `Xdoclint:all`/`Xwerror` similarly moved into a
  `plugins.withId("maven-publish")` guard, which correctly reaches alterego/incognito (both
  publish a javadoc jar) and skips effigies (a CLI, deliberately not published) without a bespoke
  flag. No behavioural change to alterego's own reports or doclint enforcement.

### incognito

- **`DirectIdStrategy.ALTEREGO_NINO`, `.ALTEREGO_NHS_NUMBER`, `.ALTEREGO_PASSPORT_NUMBER`, and
  `.ALTEREGO_DRIVING_LICENCE_NUMBER` added**, wiring all four remaining `alterego` GB identifier
  generators (already present, unwired - ADR 0012) through to `TableTransformLoadStage`
  (fabrication), `AnonymisationReportBuilder` (illustrative DPIA samples), and `VerificationStage`
  (new fictionality checks: every fabricated value must carry its guaranteed-fictional prefix -
  `QQ` / `999` / `ZZ` / `99999` respectively - alongside the existing email/postcode/domain/URL
  checks). Each covered by its own live E2E test against a real Postgres. `creditCardNumber()` is
  the one identifier builtin still unwired - deliberately, since a card number is `SENSITIVE`
  (confirmed, same treatment as a bank account), not a plain `DIRECT_ID` - see the
  `redactionConstant` entry below for how that gap actually closed.
- **`ColumnPolicy.redactionConstant` added** - an optional caller-chosen fixed placeholder for a
  `RedactionStrategy.CONSTANT` column (e.g. `"0000 0000 0000 0000"` for a card number), instead of
  the generic `"REDACTED"` every `CONSTANT`-redacted text column got before. Text-type columns
  only; a non-text column with one set fails closed with a clear `ConfigException` at
  pipeline-build time, not per row (SPEC §7.2). Wired through `YamlPolicyParser` and the DPIA
  report's illustrative samples too. This is the closure for the credit-card-number gap above -
  redact to one obviously-fake constant, rather than fabricating a typed per-row value - and is
  general-purpose beyond credit cards (any `SENSITIVE distinguishing: true` text column wanting a
  specific placeholder).
- **`TableTransformLoadStage` now opens one `alterego` `RecordScope` per source row**, keyed on the
  row's own source PK (deterministic, reproducible-mode-safe), and routes every
  `DIRECT_ID`/`UNIQUE_CANDIDATE_KEY` typed generator through it instead of calling the
  transformation bare. A no-op for every strategy except `ALTEREGO_CITY`/`ALTEREGO_POSTCODE`/
  `ALTEREGO_PHONE` - the only three that ever consult record-scoped attributes - so a table
  classifying two or more of those three now fabricates them coherently: same fictional UK region
  within a row, never independently-picked unrelated parts of the country. Verified with a new
  `RecordCoherenceE2ETest` against a real Postgres, reusing the same area-to-dialling-code mapping
  `alterego`'s own `RecordCoherenceIntegrationTest` treats as ground truth. `SPECIFICATION.md`
  §4.1/Appendix A updated.
- **Spotless/SpotBugs config consolidated to the monorepo root** alongside the PMD move below -
  `config/spotbugs/exclude-incognito.xml`; see the alterego entry above for the mechanics, including
  the find-sec-bugs and JUnit BOM alignment.
- **`PolicyInferrer` and `AnonymisationPolicy.Builder.autoInfer(boolean)` are now
  `@Deprecated(forRemoval = true)`.** Inference is authoring, not execution - the maintained version
  has lived in `effigies`' own `PolicyInferrer` since the 1.0.0 split - and this copy is scheduled
  for removal at incognito's next major version (see `docs/adr/0023-authoring-above-the-engine.md`).
  No behavioural change yet; this is the
  deprecation notice ahead of that removal.
- **Identifier quoting fixed in both dialect handlers.** `PostgresDialectHandler` (`buildInsertSql`,
  `preLoadTable`'s owner-mode fallback, `postLoadTable`, `resyncSequence`) and
  `GenericDialectHandler.buildInsertSql` now quote every raw table/column identifier - previously
  only the FK drop/recreate path did. A reserved-word or mixed-case table/column name broke
  inconsistently depending on which code path touched it; none of the benchmark fixtures happen to
  use such names, so this was silent until now.
- **PMD added to the build**, sharing a root `config/pmd/ruleset.xml` with `alterego`/`effigies`.
  Fixed what it found: a `SchemaDiscoveryStage.validateTablePolicy` parameter that never actually
  gated anything (the fail-closed suggestion hint was always included, regardless of `autoInfer`);
  `DefaultIncognitoPipeline`'s catch split into `IncognitoException`/`Exception` branches instead of
  an `instanceof` check; the in-memory stores declare `Map` fields instead of `ConcurrentHashMap`; a
  couple of dead/redundant bits of code (an always-overwritten initializer, a no-op
  catch-and-rethrow). No behavioural change.
- **JaCoCo added to the build**, sharing the root `subprojects { }` block's report/`check`-task
  config with alterego/effigies (previously alterego-only; see the alterego entry above).
- **`YamlPolicyParser` no longer crashes on a `scaffold`-shaped draft policy.** Every `role:`/
  `surrogateStrategy:`/`directIdStrategy:`/`quasiIdStrategy:`/`redactionStrategy:` key `scaffold`
  writes is present with a blank (YAML `null`) value, by design - `containsKey(...)` is true for
  those, so each was resolved via `EnumType.valueOf(String.valueOf(null).toUpperCase())`, i.e.
  `EnumType.valueOf("NULL")`, throwing an unhandled `IllegalArgumentException` instead of leaving
  the field unset for the existing fail-closed validation to report clearly. Found running the
  effigies quickstart's new `setup`/`run` workflow (below) against a real database for the first
  time - previously masked because live testing had always used a fully-classified policy.
  Now checks the value itself (`!= null`), not just key presence; the fail-closed error is once
  again the clear, column-by-column message `scaffold`'s own output promises.

### effigies

- **Fixed: `identigon.jar` could not connect to a real PostgreSQL database at all.** effigies never
  declared a runtime dependency on the PostgreSQL JDBC driver - incognito is deliberately
  driver-agnostic (works against any caller-supplied `DataSource`) and only pulls the driver in
  `testRuntimeOnly` scope for its own Testcontainers tests, so nothing in the dependency graph ever
  put `org.postgresql.Driver` on the CLI's own runtime classpath, despite the jar-merging task's
  own comment claiming "JDBC drivers" were bundled. `SimpleDataSource`'s
  `DriverManager.getConnection(...)` therefore always threw `SQLException: No suitable driver
  found`, surfaced to the user as an opaque `Failed to inspect schema` with the real cause
  swallowed. Every documented `java -jar build/libs/identigon.jar discover/scaffold/run` example -
  in this repo's own READMEs and the public Getting Started guide - was unusable exactly as
  written. Found running the effigies quickstart's `run-quickstart.sh`/`.ps1` (below) against a
  real database for the first time; `runtimeOnly(libs.postgresql)` added to
  `effigies/build.gradle.kts` fixes it.
- **Added a `quickstart/` worked example** (originally `effigies/examples/quickstart/`; moved to
  the repository root in a later restructuring) - a small first-party PostgreSQL schema
  (`customers`/`orders`/`support_tickets`, no third-party data, no Docker/Testcontainers
  dependency) with a hand-authored `policy.yaml` and a step-by-step README, so evaluating the
  `discover` -> `scaffold` -> `run` workflow no longer requires a real production database or one of
  incognito's Docker-gated benchmark fixtures. Linked from the main README's new "Try it in five
  minutes" section. Demonstrates every `DirectIdStrategy`/`QuasiIdStrategy` family in one schema,
  including the new `ALTEREGO_NINO` (see the incognito entry above) and,
  deliberately, the `ALTEREGO_GENERIC` fallback for a bank-account column with no typed generator
  yet.
- **Added `quickstart/run-quickstart.sh` (POSIX `sh`) and `run-quickstart.ps1`
  (PowerShell)** - twin, behaviourally-identical scripts; Docker + Java 25 only, nothing else to
  install. `run-quickstart` (no args) is a one-shot demo: starts a throwaway Postgres container,
  loads the schema and sample data, builds the CLI jar if needed, runs
  `discover` -> `scaffold` -> `run` against the finished `policy.yaml`, and prints the fabricated
  rows plus the DPIA report location. `setup` / `run` instead exercises the real authoring
  workflow: `setup` stops after `scaffold`, leaving a draft for the `identigon-policy-author` Agent
  Skill (or a human) to classify by hand, and `run` reuses the same container and anonymises
  against whatever policy results - failing closed with a clear error if any column is still
  unclassified (see the incognito `YamlPolicyParser` fix above - this is the workflow that found
  it). `clean` tears down the throwaway container and any generated files either way. The manual
  step-by-step walkthrough in the example's README is unchanged, for anyone who wants to run or
  understand each step without a script. Both scripts were exercised end to end (all four
  commands, both fresh-container and container-reuse paths, and the fail-closed path) against a
  real Docker Desktop + PostgreSQL, which is how the two bugs above were actually found - not just
  written and assumed correct. Two portability bugs fixed along the way, neither Bash/PowerShell
  version-specific: the readiness check could pass against the official Postgres image's brief
  *temporary* startup instance (for `initdb`) moments before it restarts for the real listener, a
  narrow window where a query could hit the socket mid-restart - now requires two consecutive
  successful `pg_isready` checks, not one. Windows-only wrinkles, one per script: the `sh` version
  now avoids `docker inspect -f '{{...}}'` (MSYS2/Git-Bash mangles `{{ }}` template arguments to
  native Windows executables) in favour of a template-free `docker ps -q -f name=...` check, and
  uses `printf '%s\n'` instead of `echo` for any value that might contain backslashes - POSIX
  `echo` is free to interpret them as escapes, and every Windows path has some; the `.ps1` version
  avoids naming a parameter `$Args` (PowerShell's own reserved automatic-variable name), which
  silently breaks splatting it onward to a wrapped command.
- `PolicyInferrer` gains heuristics for postcodes (`QUASI_ID`), passport numbers, driving licence
  numbers, and credit card numbers (`DIRECT_ID`), and anchors the email/phone patterns to the end
  of the column name so a boolean like `email_verified` no longer gets suggested as DIRECT_ID.
- `ScaffoldCommand` writes its output as UTF-8 explicitly, not the platform-default charset (not
  UTF-8 on Windows) a bare `FileWriter` used.
- CLI error paths report the exception itself, not just its (often empty) message.
- Added tests for `RunCommand`, `DiscoverCommand`, `PolicyInferrer`, and `SimpleDataSource`
  (previously untested), splitting the CLI commands into a directly-testable core to do it without
  needing to fake environment variables.
- **PMD added to the build**, sharing a root `config/pmd/ruleset.xml` with `alterego`/`incognito`.
  No behavioural change here - only a `StringBuilder` under-sized for what it accumulates.
- **Spotless/SpotBugs config consolidated to the monorepo root** -
  `config/spotbugs/exclude-effigies.xml`; see the alterego entry in this section for the mechanics,
  including the find-sec-bugs and JUnit BOM alignment.
- **The runnable jar is now `identigon.jar`, not `effigies.jar`.** Consumers only ever run this
  one artifact (alterego/incognito stay internal, sibling-project dependencies), so the jar name,
  the `--version`/`--help`/usage banner text, and the manifest `Implementation-Title` now say
  "Identigon" instead of "Effigies". The Gradle module, its `org.identigon.effigies` package, and
  the `EffigiesCli` class name are unchanged - this is the public artifact name only, not a module
  rename.
- **JaCoCo added to the build**, sharing the root `subprojects { }` block's report/`check`-task
  config with alterego/incognito (previously alterego-only; see the alterego entry in that
  section). Javadoc/doclint enforcement stays deliberately absent here - effigies is a CLI, not a
  published library.

### monorepo

- **Markdown line-length lint added, then the full default `markdownlint-cli2` rule set enabled.**
  `.markdownlint-cli2.jsonc` runs `markdownlint-cli2` as a pre-commit hook. It started `MD013`-only
  (100-column line length; code blocks, tables, and headings exempt), with existing violations
  fixed in the same change. Every other default rule was then audited and turned on: `MD013` gained
  `stern` mode (forgives a bare long token alone on its own line - typically a URL - but not one
  sharing a line with prose); `MD024` (no-duplicate-heading) uses `siblings_only` so Keep a
  Changelog's repeated `### Added`/`### Fixed` headings per version stay legal; the rest (`MD004`,
  `MD007`, `MD012`, `MD022`, `MD031`, `MD032`, `MD034`, `MD036`, `MD038`, `MD040`, `MD060`, ...)
  needed no config, just fixing the content across the repo's markdown files.
- **Dependabot added** (`gradle` + `github-actions`, weekly) to keep dependency versions current
  across the monorepo.
- **CI reports made downloadable.** `_build.yml` uploads each matrix leg's (`ubuntu-latest`/
  `windows-latest`) JUnit XML, JaCoCo (HTML/XML/CSV), PMD (HTML/XML), and SpotBugs (HTML) reports
  as a `build-reports-<os>` artifact via `actions/upload-artifact`, even when the build step fails
  - previously these only existed buried in the raw Gradle log.
- **Gradle version catalog added** (`gradle/libs.versions.toml`). Every shared version - root
  plugin versions, SpotBugs/PMD `toolVersion`s, find-sec-bugs, the JUnit BOM, snakeyaml, H2, the
  Testcontainers BOM, the Postgres driver - was a literal string repeated in one or more
  `build.gradle.kts` files; all ten now have exactly one declaration. No version actually changed
  (verified: the published `incognito` POM still resolves `snakeyaml` to `2.2`, matching before).
  CI's downloadable per-run artifact (see above) makes this easy to spot-check going forward: the
  JaCoCo/PMD/SpotBugs reports inside it reflect whatever the catalog resolved.

## [alterego-0.1.0] - 2026-07-26

Initial implementation, milestones M0-M6 of `alterego/PLAN.md` (now deleted; see git history).

### Added

- Deterministic pseudonymisation core: per-input HMAC-SHA256 key derivation, counter-mode randomness
  stream, sampling primitives (Appendix A), frozen conformance vectors.
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
- **Owner-mode cyclic-FK load** (SPEC §9): a non-superuser target that *owns* its tables now clones
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
- `JITTER_DAYS` no longer raises spurious per-period volume-drift *warnings*: because a ±N-day
  jitter crosses month boundaries, the verification volume check now buckets it **yearly** (not
  monthly), where a day-window barely leaks. Cosmetic - it never failed the run.

### Known gaps

- Composite PK **and** cyclic FK on the same table (each supported alone; the combination fails
  closed - `FailClosedGuardE2ETest`). Tracked in root `PLAN.md`
  (`docs/tasks/incognito-composite-pk-cyclic-fk.md`).
- Generic shape-preserving fabrication (`ALTEREGO_GENERIC` / string-`SYNTHESISE`) carries no
  fictionality guarantee - inherent for an arbitrary shape; use a typed strategy where the guarantee
  matters. It runs on alterego's `bind` extension API (salt-keyed, deterministic) and lives in
  Incognito by decision, not a delegation gap (see ADR 21).
- Declarative table **partitioning** is not cloned (partition children are discovered but the
  partitioned parent isn't specially handled); the Pagila benchmark excludes its partitioned
  `payment` table. Non-partitioned tables are unaffected.

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

### alterego

- Merged into the `identigon` monorepo alongside `incognito` and `effigies`, each a Gradle
  subproject with full history preserved. Versioning is now lockstep across all three, sourced from
  the monorepo root (see `docs/adr/0024-lockstep-versioning.md`). A deliberate re-baseline to
  1.0.0, not a claim that four minor versions' worth of API changes happened at once - alterego's
  own history before this point is the `alterego-0.1.0`-`0.4.0` entries above.

### incognito

- Merged into the `identigon` monorepo alongside `alterego` and `effigies`, each a Gradle
  subproject with full history preserved. Versioning is now lockstep across all three, sourced from
  the monorepo root (see `docs/adr/0024-lockstep-versioning.md`). Moves backward in number from
  incognito's last independent release, `1.1.0` -> `1.0.0` - deliberate, not a downgrade; see the
  ADR.
- **`DirectIdStrategy`: `ALTEREGO_POSTCODE`, `ALTEREGO_DOMAIN`, `ALTEREGO_URL`.** Three previously
  unexposed `alterego` typed generators (`postcode()`, `domainName()`, `url()`) are now reachable
  from policy. `VerificationStage` positively asserts each strategy's fictionality guarantee on the
  target (GB postcode inward-code letter; RFC 2606 reserved domain/TLD for domain/URL), and these
  strategies are excluded from the generic DIRECT_ID survival check, same as `ALTEREGO_EMAIL`.

### effigies

- Merged into the `identigon` monorepo alongside `alterego` and `incognito`, each a Gradle
  subproject with full history preserved. Versioning is now lockstep across all three, sourced from
  the monorepo root (see `docs/adr/0024-lockstep-versioning.md`). The version number itself
  is unchanged from effigies' prior standalone release - both are 1.0.0 - this entry exists so that
  change in what "1.0.0" means isn't silently missing from the record.
