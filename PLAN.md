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
