# Identigon - Implementation Plan

Single ranked backlog, most important first - covers this monorepo and, per
`docs/adr/0033-extend-documentation-coverage-to-identigon-github-io.md`, the separate
`identigon.github.io` site repository too. Entries are **deleted** when done, never annotated - a
plan that accumulates completed items stops being read. **One paragraph each** - see
[DOC-MAP.md](DOC-MAP.md). Each entry carries a `**Type:**`/`**Importance:**`/`**Effort:**` line, and
optionally a `**Project:**` tag (`alterego` / `incognito` / `effigies` / `identigon.github.io`) for
work scoped to one subproject or repository; an untagged entry is cross-cutting or applies to no
single one. See the root `CHANGELOG.md` for what's already shipped.

## Quote every SQL identifier incognito builds

**Type:** bug - **Importance:** high - **Effort:** medium **Project:** incognito

The dialect handlers quote table and column names, but the stages above them concatenate raw catalog
names - the source `SELECT`, the deferred cyclic-FK `UPDATE`, the non-empty-target guard, the
compensation `DELETE`, and every verification query - so a mixed-case or reserved-word name
(`"Customer"`, `order`) fails the run at the first source read. The `pg_stats` lookup also splices
names into string literals. Call sites and approach in `docs/tasks/incognito-identifier-quoting.md`.

## Make incognito schema-aware instead of relying on `search_path`

**Type:** bug - **Importance:** high - **Effort:** high **Project:** incognito

Tables are identified by bare name and every statement resolves through each connection's
`search_path`: discovery sees only the source's current schema, other schemas are silently absent
from the clone, and the owner-mode FK drop/recreate matches `pg_constraint` by bare name across all
namespaces, so a same-named table in another target schema can have its constraints dropped and
recreated. Start with one explicit, fully qualified schema; multiple schemas later. See
`docs/tasks/incognito-schema-qualified-names.md`.

## Prove fail-closed holds when the schema drifts after approval

**Type:** debt - **Importance:** high - **Effort:** medium **Project:** incognito

Fail-closed is the property the privacy argument rests on, but it is only tested against a static
schema. Nothing approves a policy and then applies the changes a migration would - a new PII column,
a rename, a type change, a new or dropped FK, a new constraint, a database-specific type - before
the next `validate`/`run`. The property to prove is that no path lets an unclassified or
wrongly-typed source value reach the target unnoticed. Scenarios, and the spec gaps they expose, in
`docs/tasks/incognito-schema-drift-tests.md`.

## Make the scaffolded draft explain itself

**Type:** feature - **Importance:** high - **Effort:** medium **Project:** effigies

Users find the quickstart steps easy to follow but the `scaffold` output intimidating: it doesn't
say what a role or a strategy is, which other settings a column needs, what values each one allows,
which combinations are valid, or what to do next. Its only pointers are a spec section and a Javadoc
page. Start the file with what to do and a legend of roles, list the allowed values next to each
setting, give reasons instead of heuristic names, end with a summary and the next command, and have
`validate` report what is still missing per column. See
`docs/tasks/effigies-self-explaining-scaffold.md`.

## Present Identigon as one command-line tool

**Type:** docs - **Importance:** high - **Effort:** medium

Users want to run `identigon`, not learn that it is built from `alterego`, `incognito` and
`effigies`, yet those names meet them everywhere: the README's first screen, the quickstart's
`../effigies/build/libs` jar path, `help` and `version` output, scaffold comments, `ConfigException`
messages that cite `SPEC §4.1`/`ADR 31` and Java type names, and an agent skill that runs a
non-existent `effigies` command. Keep the component names for library users and contributors, and
take them off the CLI user's path. Inventory in `docs/tasks/one-tool-user-surface.md`.

## Write a column-classification guide for policy authors

**Type:** docs - **Importance:** high - **Effort:** medium **Project:** identigon.github.io

The only complete description of roles, strategies and their valid combinations is
`docs/spec/incognito.md` §4.1, §6 and Appendix B - a contract for implementers, not a guide for
someone classifying columns. Write one page for policy authors: what each role means for the data
("replaced with a fictional value", "kept as-is"), a short decision path per column ("does this
identify a person on its own?"), every setting each role needs and allows, and worked examples. It
needs a stable public URL for the scaffold and `validate` output to link to, so the site is its
natural home.

## Plainer names in the policy vocabulary

**Type:** feature - **Importance:** medium - **Effort:** medium **Project:** incognito

Every `directIdStrategy` value carries an `ALTEREGO_` prefix that names an internal library rather
than what happens to the data, `ALTEREGO_GENERIC` hides that it carries no fictionality guarantee,
and `directIdStrategy` also serves as the generator hint on `QUASI_ID` columns. Needs an ADR:
unprefixed spellings, a descriptive name for the generic strategy, old spellings as deprecated
aliases or a clean break, and whether the Java enum changes or only the YAML. See
`docs/tasks/incognito-plainer-policy-vocabulary.md`.

## Read the whole source from one consistent snapshot

**Type:** bug - **Importance:** medium - **Effort:** medium **Project:** incognito

The spec assumes an offline snapshot (§1.3) but nothing provides one: each table and each
verification query opens its own READ COMMITTED source connection, so against a live database a
child row committed after its parent table was read aborts the run (no key translation), and
source-versus-target verification compares different moments. Needs an ADR choosing between one
shared `REPEATABLE READ` connection and an exported snapshot. See
`docs/tasks/incognito-consistent-source-snapshot.md`.

## Full-pipeline tests for awkward identifiers and multi-schema databases

**Type:** debt - **Importance:** medium - **Effort:** medium **Project:** incognito

Quoting and schema handling are tested only inside the dialect handler; no test runs discovery,
load, deferred cyclic-FK update, verification and compensation over mixed-case or reserved-word
names, or against a target with a decoy schema of same-named tables. These are the acceptance tests
for the two entries above and land with them. Scenarios in
`docs/tasks/incognito-identifier-and-schema-e2e-tests.md`.

## Accept an encoded, high-entropy `IDENTIGON_SALT`

**Type:** feature - **Importance:** medium - **Effort:** low **Project:** effigies

effigies takes `IDENTIGON_SALT`'s raw UTF-8 bytes and only checks for 16 of them, so a guessable
passphrase - like the README's own `my-secret-salt-bytes` - weakens every persistent or reproducible
run. Accept `hex:`- and `base64:`-prefixed values (32 random bytes recommended, e.g.
`openssl rand -hex 32`); keep a bare value as raw UTF-8 so existing salts reproduce the same output,
but print a warning for it; switch the README, quickstart and spec to the encoded form. The one
incompatibility: an existing bare salt that itself begins `hex:` or `base64:` would change meaning -
call that out in the changelog.

## Break up `TableTransformLoadStage` and `VerificationStage`

**Type:** debt - **Importance:** medium - **Effort:** high **Project:** incognito

Both are about 1,200 lines, held under PMD only by excluding its size and complexity rules.
`VerificationStage.process` alone runs six independent checks in ~470 lines, nine fictionality
checks are near-copies of one query, and every column transformer takes eight parameters. The
quoting, schema and snapshot work above touches nearly every SQL statement in both, so splitting
them first shrinks those changes. Proposed split in `docs/tasks/incognito-split-large-stages.md`.

## Say what a run guarantees: de-identification, not proven anonymity

**Type:** docs - **Importance:** medium - **Effort:** medium

The README opens with "Identigon anonymises databases", and the DPIA report can read as evidence
that a DPIA is satisfied. Replacing direct identifiers does not by itself make a dataset anonymous,
and the structural-uniqueness analysis scores one FK edge at a time. State the guarantee as
de-identification with fictional data. Present the report as evidence that supports a DPIA, have it
separate direct identifiers, quasi-identifiers, structural uniqueness and residual risk, and have it
state what its re-identification analysis does not cover. Changing the project's own terminology may
need an ADR.

## Define and test what an interrupted run leaves behind

**Type:** debt - **Importance:** medium - **Effort:** medium **Project:** incognito

A production-sized clone can run for hours, but nothing tests a kill, a lost source or target
connection, or a database failure partway through a load. Pin what happens to the target (the
compensation `DELETE` of spec §8.1, or a partial load), say whether a run can be resumed or must be
restarted from an emptied target, and make sure a partial target can never be mistaken for a
finished clone - e.g. no DPIA report, and a non-zero exit code a scheduler can't miss.

## Measure memory and throughput on a large database

**Type:** debt - **Importance:** medium - **Effort:** medium **Project:** incognito

Key translation is held in memory and the benchmarks are small sample databases, so there is no
evidence of how memory grows with row count, how fast a large clone runs, or where it runs out of
heap. Add a generated large-volume benchmark (tens of millions of rows, wide FK fan-out) that
records peak heap and rows per second, and publish the numbers with a sizing rule of thumb. This is
the evidence the persisted `RedisKeyTranslationStore` entry needs before it moves up.

## Make human review of agent-drafted policies explicit

**Type:** docs - **Importance:** medium - **Effort:** low

The `identigon-policy-author` skill already never assigns a role without confirmation, but the docs
don't say where the trust boundary is. Say plainly in the quickstart, the skill and the site that an
agent only helps draft `policy.yaml`: a person reviews and owns every classification, and safety
comes from the deterministic parts - the checked-in policy, `validate`, fail-closed `run`, and the
source-value survival checks - never from the agent. A plausible wrong classification has privacy
consequences.

## Record-level cross-field coherence in policies

**Type:** feature - **Importance:** medium - **Effort:** high **Project:** incognito

Policies get coherence only for UK region (city/postcode/phone within a row) and for jittered dates
sharing a `coherenceGroup`. Realistic test data often needs more: title agreeing with first name,
address lines agreeing with each other, ordering constraints between dates without a shared jitter,
identifiers derived from other fields. `alterego`'s `RecordScope` (ADR 8/9) already provides the
mechanism; what's missing is a way for a policy to declare it. This is the `RecordScope` item the
quickstart/Agent Skill revisit entry refers to.

## Stop implying generic JDBC support

**Type:** docs - **Importance:** medium - **Effort:** low

The engine is built on JDBC with a `GenericDialectHandler`, but only PostgreSQL is tested and tuned.
Metadata, identifier handling, generated keys, constraints, temporal types and DDL all differ
between engines, which is exactly where a schema-cloning tool breaks. Say in the README, the site
and the spec that PostgreSQL is the one supported database, and treat any other engine as untested
until an entry like the one below proves it.

## Prove a second database engine against a realistic schema

**Type:** feature - **Importance:** medium - **Effort:** high **Project:** incognito

Oracle is the likeliest candidate for organisations with older estates, then SQL Server. Needs a
dialect handler, a Testcontainers fixture with a realistic schema (not a clean sample),
engine-specific type mapping for `SYNTHESISE`/jitter, and the full verification suite passing on
that engine. Until this lands, portability is not a claim the project makes.

## Ship `identigon` as a self-contained executable

**Type:** feature - **Importance:** medium - **Effort:** high **Project:** effigies

Running the CLI today means installing Java 25 and calling `java -jar identigon.jar`, or building
the repository for the quickstart. A `jlink`/`jpackage` bundle per platform (or a native image, if
the JDBC drivers and SnakeYAML allow) would give users an `identigon` command with no runtime to
install. That matches the one-tool presentation above and removes the Java 25 barrier for
organisations standardised on older runtimes.

## Revisit excluding `effigies.jar` from the GitHub Release assets

**Type:** feature - **Importance:** low - **Effort:** low **Project:** effigies

ADR-0028 deliberately left the thin `effigies.jar` out of the Release asset set (`alterego.jar`,
`incognito.jar`, `identigon.jar` only) on the reasoning that it can't run standalone - anyone
wanting effigies as a library uses GPR (`org.identigon:effigies`), and anyone wanting to run it uses
the fat `identigon.jar`. Revisit this decision before treating it as settled.

## A non-interactive "authoring session" mode

**Type:** feature - **Importance:** medium - **Effort:** high **Project:** effigies

Runs discover -> scaffold -> (agent) -> run in one invocation, with the DPIA report fed back for
iteration.

## Support for engines `incognito` adds beyond PostgreSQL

**Type:** docs - **Importance:** low - **Effort:** low **Project:** effigies

No change needed here when it lands - a placeholder confirming this was checked, not a task.

## Revisit `quickstart/` and the Agent Skill once `incognito`'s policy-API backlog lands

**Type:** docs - **Importance:** low - **Effort:** medium **Project:** effigies

Several `alterego` capabilities `incognito` doesn't expose yet (remaining identifier generators, a
bank-account generator, `RecordScope` cross-field coherence, jitter/clamp knobs, a `pattern(String)`
strategy - see the matching `incognito`-tagged entries below) are candidates to fold into the
quickstart policy and teach the `identigon-policy-author` skill to suggest, as each lands.

## Composite PK + cyclic FK together

**Type:** feature - **Importance:** medium - **Effort:** high **Project:** incognito

Currently fails closed with a clear message rather than corrupting data; not exercised by any
benchmark. Bigger than "widen the pass-2 `UPDATE` to key on every PK column" - the real gap is
deferred _composite-FK_ resolution into a cyclic table (a composite-PK table can only be in a cycle
if a composite FK references it, which hits a separate guard first). See
`docs/tasks/incognito-composite-pk-cyclic-fk.md` for the full analysis and handoff.

## Move narrative comments to the documents they belong in

**Type:** debt - **Importance:** low - **Effort:** medium

Many comments recount history ("X used to ..."), weigh alternatives an ADR already records, or park
TODOs - facts `DOC-MAP.md` files in `CHANGELOG.md`, `docs/adr/` or here. Main code and the Gradle
scripts are done; test code (21 comments across 18 classes), `.pre-commit-config.yaml`, the
workflows and long rationale blocks remain. See
`docs/tasks/move-narrative-comments-to-their-documents.md`.

## Split YAML policy parsing into an `incognito-yaml` module

**Type:** debt - **Importance:** low - **Effort:** medium **Project:** incognito

`YamlPolicyParser` pulls SnakeYAML into incognito's core as an `implementation` dependency, so every
consumer of the programmatic API carries a YAML parser it may never use. Moving it to its own module
(effigies depending on both) keeps the core dependency-lean, as `docs/spec/incognito.md` §10
intends.

## Let `run` write its DPIA reports somewhere other than the working directory

**Type:** feature - **Importance:** low - **Effort:** low **Project:** effigies

`run` always writes `dpia-report.html`/`.json`/`.md` into the current directory, overwriting any
from an earlier run, with no option to put them elsewhere - awkward in CI and when running several
policies from one directory. A `--report-dir <dir>` option (default: the working directory, as
today) would close this; `RunCommand.run` already takes a report directory internally.

## Handle `saltMode` as the `SaltMode` enum in effigies' `run`

**Type:** debt - **Importance:** low - **Effort:** low **Project:** effigies

`RunCommand` parses the policy into a typed `SaltMode`, then lower-cases it to a string and compares
against `"persistent"`/`"reproducible"` literals in several places. Passing the enum through and
switching on it removes the string round-trip and lets the compiler catch a new mode that isn't
handled.

## Enable Gradle's configuration cache

**Type:** debt - **Importance:** low - **Effort:** medium

Every invocation, even `./gradlew :alterego:test`, reconfigures all three subprojects from scratch
and shells out to `git describe` and `docker info` from the root build script. The configuration
cache would reuse the configuration between runs, but the two `providers.exec` probes are its inputs
and still run each time to validate it - so the Docker probe (needed only by incognito's coverage
minimum) should move out of configuration, e.g. into the coverage-verification task itself. Needs
`org.gradle.configuration-cache=true` plus a pass over the build scripts and plugins (SpotBugs,
Spotless) for compatibility problems.

## Pass `release.yml`'s `tag` input to shell through `env:`

**Type:** bug - **Importance:** low - **Effort:** low

The "Derive the release version" step interpolates `${{ inputs.tag }}` straight into its script, so
a crafted tag value would run as shell. Only users with write access can dispatch the workflow,
which limits the risk, but GitHub's hardening guidance is to pass inputs through `env:` and quote
the variable.

## Record why Java 25 is the minimum runtime

**Type:** docs - **Importance:** low - **Effort:** low

Evaluators ask whether Java 25 is a real technical requirement or only the development baseline. The
code relies on recent language features (records, sealed interfaces, pattern matching), but nothing
records the choice or what an older baseline would cost. Write it down as an ADR and link it from
the README's requirements.

## `ServiceLoader`-based strategy/dictionary packs for additional countries

**Type:** feature - **Importance:** low - **Effort:** high **Project:** alterego

Post-v1 extensibility: distribute new-country dictionaries and strategies as separate artifacts
rather than bundling every country in core.

## Language-sensitive generation

**Type:** feature - **Importance:** low - **Effort:** high **Project:** alterego

v1 built-ins resolve entirely by the locale's country; the language component steers nothing yet.
Unused so far - no concrete need has surfaced.

## Pattern-language extensions: character classes and repetition counts

**Type:** feature - **Importance:** low - **Effort:** medium **Project:** alterego

`pattern(String)`'s `D`/`L`/`l`/`A` mini-language has no `[ABC]` character-class or `D{5}`
repetition syntax. A genuine specification contract change (new syntax, new exception cases, new
conformance tests) - do deliberately, not as a drive-by. `incognito`'s planned `ALTEREGO_PATTERN`
strategy doesn't need this to land first (today's syntax is already enough to wire) but should be
revisited to expose any richer syntax added here.

## External `MappingStore` modules (JDBC, Redis)

**Type:** feature - **Importance:** low - **Effort:** medium **Project:** alterego

Build against the existing contract test once a real need appears; a local file-backed store already
ships in core (see the file-backed-mapping-store ADR).

## A public codec SPI for caller-supplied value types

**Type:** feature - **Importance:** low - **Effort:** high **Project:** alterego

The fixed value-type set was deliberately rejected as a public SPI (see the fixed-value-type-set
ADR); revisit only if a real need appears.

## Fictional-range additions: TEST-NET IP addresses (RFC 5737)

**Type:** feature - **Importance:** low - **Effort:** low **Project:** alterego

Same fictional-by-default family as the existing built-ins' reserved ranges.

## `companyNumber()` (Companies House) - blocked, unsolved fictional space

**Type:** feature - **Importance:** low - **Effort:** high **Project:** alterego

No reserved/test range and no checksum exist for UK company numbers; the only
structurally-impossible value is zero, and mapping every company to zero is redaction, not
pseudonymisation. A high range is time-dependent (Scotland is already at `SC770005`). Deferred until
a reserved or never-issued range is found - full analysis in
`docs/research/0002-alterego-fictional-ranges.md`. Has a regional element (`SC`/`NI`/plain) that
would feed `UK_NATION` record coherence if it returns.

## Multi-edge structural fingerprints

**Type:** feature - **Importance:** low - **Effort:** high **Project:** incognito

The shipped structural-uniqueness report scores one FK edge at a time; combining several edges into
one joint fingerprint per subject is more faithful to real singling-out but harder to threshold
defensibly.

## Attribute + structure combined findings

**Type:** feature - **Importance:** low - **Effort:** medium **Project:** incognito

A kept `distinguishing: false` value plus a rare FK fan-out is a stronger fingerprint than either
alone; not modelled today.

## Declarative-partitioning support

**Type:** feature - **Importance:** medium - **Effort:** medium **Project:** incognito

The Pagila benchmark excludes the partitioned `payment` table because partition children are
discovered as plain tables with no special handling. A proper treatment recognises the parent/child
relationship (`pg_partitioned_table`/`pg_inherits`), clones by inserting into the parent (letting
Postgres route to partitions), and skips the children - preserving per-partition volumes.

## `RedisKeyTranslationStore` - a persisted, out-of-process key store

**Type:** feature - **Importance:** low - **Effort:** high **Project:** incognito

v1.0 ships only an in-memory store (Redis is an explicit v1.0 non-goal). Would let key translation
outlive a single JVM run - useful for very large clones, resuming an interrupted load, and cross-run
surrogate stability. A persisted key store is itself sensitive and must be destroyed on successful
completion, exactly as the salt is.

## No UK bank-account generator in `alterego`

**Type:** feature - **Importance:** medium - **Effort:** medium **Project:** incognito

Unlike the other UK identifiers, there's no primitive to wire even if `DirectIdStrategy` grew a case
for it. Surfaced authoring a bank-account column for the effigies quickstart example, which falls
back to `ALTEREGO_GENERIC` (no fictionality guarantee) in the meantime.

## Temporal `QUASI_ID` jitter never touches the time-of-day component

**Type:** feature - **Importance:** medium - **Effort:** medium **Project:** incognito

Every temporal strategy zeroes seconds or does date-only arithmetic; a `TIMESTAMP` column's clock
time always survives exactly as-is. `alterego` already supports jittering it (`TimeField.HOUR`, an
explicit range, a seconds half-range, `shiftInstant()`) - none of it reachable from a policy today.

## No way to clamp a jittered `QUASI_ID` date/time to "not in the future"

**Type:** feature - **Importance:** low - **Effort:** low **Project:** incognito

`alterego`'s `JitterOptions.max(...)` already supports an inclusive upper bound (the
caller-supplied-bound principle - see the explicit-clamp-bounds ADR), but `incognito` never passes
one. A wide `JITTER_DAYS` shift on a date near "today" in the source can jitter into the future.
Narrower than a fully general clamp: cap at the run's own captured "now".

## `AlterEgo.pattern(String)` has no `DirectIdStrategy` equivalent

**Type:** feature - **Importance:** low - **Effort:** medium **Project:** incognito

No way for a policy to hand a custom shape pattern for a code-like column that isn't a
name/email/phone/etc. A `DirectIdStrategy.ALTEREGO_PATTERN` with a `pattern` field on `ColumnPolicy`
would close this - not blocked on `alterego`'s pattern-language extensions item, though richer
syntax there should be revisited here if it lands.

## `AlterEgo.creditCardNumber()` still unused by incognito

**Type:** feature - **Importance:** low - **Effort:** low **Project:** incognito

Its practical gap was closed instead via `ColumnPolicy.redactionConstant` (a fixed placeholder,
arguably a better privacy story than per-row fabrication for a `SENSITIVE` field). Wiring the typed
generator itself remains possible but is no longer blocking anything.

## Publish generated Javadoc and link it in from the site

**Type:** feature - **Importance:** low - **Effort:** medium **Project:** identigon.github.io

`alterego`/`incognito`'s Javadoc is built here but not published anywhere yet. Deliberately not
copied into `identigon.github.io` - exactly one source of truth for anything derived from the code
(`docs/adr/0034-identigon-github-io-as-a-separate-repository.md`) - needs a publishing step in this
repo's own CI, then a link added on the site once that exists.

## Per-subproject pages on the site

**Type:** docs - **Importance:** low - **Effort:** medium **Project:** identigon.github.io

`alterego` / `incognito` / `effigies` each get only a one-line feature-grid blurb on the landing
page today. Worth a page each once there's more to say than that summary.

## Site search

**Type:** feature - **Importance:** low - **Effort:** low **Project:** identigon.github.io

VitePress has built-in local search support; not worth turning on until there's enough content on
the site to search.
