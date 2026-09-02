# Identigon - Implementation Plan

Single ranked backlog, most important first - covers this monorepo and, per
`docs/adr/0033-extend-documentation-coverage-to-identigon-github-io.md`, the separate
`identigon.github.io` site repository too. Entries are **deleted** when done, never annotated - a
plan that accumulates completed items stops being read. **One paragraph each** - see
[DOC-MAP.md](DOC-MAP.md). Each entry carries a `**Type:**`/`**Importance:**`/`**Effort:**` line,
and optionally a `**Project:**` tag (`alterego` / `incognito` / `effigies` / `identigon.github.io`)
for work scoped to one subproject or repository; an untagged entry is cross-cutting or applies to
no single one. See the root `CHANGELOG.md` for what's already shipped.

## Revisit excluding `effigies.jar` from the GitHub Release assets

**Type:** feature - **Importance:** low - **Effort:** low
**Project:** effigies

ADR-0028 deliberately left the thin `effigies.jar` out of the Release asset set (`alterego.jar`,
`incognito.jar`, `identigon.jar` only) on the reasoning that it can't run standalone - anyone
wanting effigies as a library uses GPR (`org.identigon:effigies`), and anyone wanting to run it
uses the fat `identigon.jar`. Revisit this decision before treating it as settled.

## JaCoCo for effigies

**Type:** debt - **Importance:** low - **Effort:** low
**Project:** effigies

Optional, consistency-only with `alterego`/`incognito`'s coverage reporting.

## A non-interactive "authoring session" mode

**Type:** feature - **Importance:** medium - **Effort:** high
**Project:** effigies

Runs discover -> scaffold -> (agent) -> run in one invocation, with the DPIA report fed back for
iteration.

## Support for engines `incognito` adds beyond PostgreSQL

**Type:** docs - **Importance:** low - **Effort:** low
**Project:** effigies

No change needed here when it lands - a placeholder confirming this was checked, not a task.

## Revisit `quickstart/` and the Agent Skill once `incognito`'s policy-API backlog lands

**Type:** docs - **Importance:** low - **Effort:** medium
**Project:** effigies

Several `alterego` capabilities `incognito` doesn't expose yet (remaining identifier generators, a
bank-account generator, `RecordScope` cross-field coherence, jitter/clamp knobs, a `pattern(String)`
strategy - see the matching `incognito`-tagged entries below) are candidates to fold into the
quickstart policy and teach the `identigon-policy-author` skill to suggest, as each lands.

## Composite PK + cyclic FK together

**Type:** feature - **Importance:** medium - **Effort:** high
**Project:** incognito

Currently fails closed with a clear message rather than corrupting data; not exercised by any
benchmark. Bigger than "widen the pass-2 `UPDATE` to key on every PK column" - the real gap is
deferred *composite-FK* resolution into a cyclic table (a composite-PK table can only be in a cycle
if a composite FK references it, which hits a separate guard first). See
`docs/tasks/incognito-composite-pk-cyclic-fk.md` for the full analysis and handoff.

## `ServiceLoader`-based strategy/dictionary packs for additional countries

**Type:** feature - **Importance:** low - **Effort:** high
**Project:** alterego

Post-v1 extensibility: distribute new-country dictionaries and strategies as separate artifacts
rather than bundling every country in core.

## Language-sensitive generation

**Type:** feature - **Importance:** low - **Effort:** high
**Project:** alterego

v1 built-ins resolve entirely by the locale's country; the language component steers nothing yet.
Unused so far - no concrete need has surfaced.

## Pattern-language extensions: character classes and repetition counts

**Type:** feature - **Importance:** low - **Effort:** medium
**Project:** alterego

`pattern(String)`'s `D`/`L`/`l`/`A` mini-language has no `[ABC]` character-class or `D{5}`
repetition syntax. A genuine specification contract change (new syntax, new exception cases, new
conformance tests) - do deliberately, not as a drive-by. `incognito`'s planned `ALTEREGO_PATTERN`
strategy doesn't need this to land first (today's syntax is already enough to wire) but should be
revisited to expose any richer syntax added here.

## External `MappingStore` modules (JDBC, Redis)

**Type:** feature - **Importance:** low - **Effort:** medium
**Project:** alterego

Build against the existing contract test once a real need appears; a local file-backed store
already ships in core (see the file-backed-mapping-store ADR).

## A public codec SPI for caller-supplied value types

**Type:** feature - **Importance:** low - **Effort:** high
**Project:** alterego

The fixed value-type set was deliberately rejected as a public SPI (see the fixed-value-type-set
ADR); revisit only if a real need appears.

## Fictional-range additions: TEST-NET IP addresses (RFC 5737)

**Type:** feature - **Importance:** low - **Effort:** low
**Project:** alterego

Same fictional-by-default family as the existing built-ins' reserved ranges.

## `companyNumber()` (Companies House) - blocked, unsolved fictional space

**Type:** feature - **Importance:** low - **Effort:** high
**Project:** alterego

No reserved/test range and no checksum exist for UK company numbers; the only
structurally-impossible value is zero, and mapping every company to zero is redaction, not
pseudonymisation. A high range is time-dependent (Scotland is already at `SC770005`). Deferred
until a reserved or never-issued range is found - full analysis in
`docs/research/0002-alterego-fictional-ranges.md`. Has a regional element (`SC`/`NI`/plain) that
would feed `UK_NATION` record coherence if it returns.

## Multi-edge structural fingerprints

**Type:** feature - **Importance:** low - **Effort:** high
**Project:** incognito

The shipped structural-uniqueness report scores one FK edge at a time; combining several edges into
one joint fingerprint per subject is more faithful to real singling-out but harder to threshold
defensibly.

## Attribute + structure combined findings

**Type:** feature - **Importance:** low - **Effort:** medium
**Project:** incognito

A kept `distinguishing: false` value plus a rare FK fan-out is a stronger fingerprint than either
alone; not modelled today.

## Declarative-partitioning support

**Type:** feature - **Importance:** medium - **Effort:** medium
**Project:** incognito

The Pagila benchmark excludes the partitioned `payment` table because partition children are
discovered as plain tables with no special handling. A proper treatment recognises the
parent/child relationship (`pg_partitioned_table`/`pg_inherits`), clones by inserting into the
parent (letting Postgres route to partitions), and skips the children - preserving per-partition
volumes.

## `RedisKeyTranslationStore` - a persisted, out-of-process key store

**Type:** feature - **Importance:** low - **Effort:** high
**Project:** incognito

v1.0 ships only an in-memory store (Redis is an explicit v1.0 non-goal). Would let key translation
outlive a single JVM run - useful for very large clones, resuming an interrupted load, and
cross-run surrogate stability. A persisted key store is itself sensitive and must be destroyed on
successful completion, exactly as the salt is.

## No UK bank-account generator in `alterego`

**Type:** feature - **Importance:** medium - **Effort:** medium
**Project:** incognito

Unlike the other UK identifiers, there's no primitive to wire even if `DirectIdStrategy` grew a
case for it. Surfaced authoring a bank-account column for the effigies quickstart example, which
falls back to `ALTEREGO_GENERIC` (no fictionality guarantee) in the meantime.

## Temporal `QUASI_ID` jitter never touches the time-of-day component

**Type:** feature - **Importance:** medium - **Effort:** medium
**Project:** incognito

Every temporal strategy zeroes seconds or does date-only arithmetic; a `TIMESTAMP` column's clock
time always survives exactly as-is. `alterego` already supports jittering it (`TimeField.HOUR`, an
explicit range, a seconds half-range, `shiftInstant()`) - none of it reachable from a policy today.

## No way to clamp a jittered `QUASI_ID` date/time to "not in the future"

**Type:** feature - **Importance:** low - **Effort:** low
**Project:** incognito

`alterego`'s `JitterOptions.max(...)` already supports an inclusive upper bound (the
caller-supplied-bound principle - see the explicit-clamp-bounds ADR), but `incognito` never passes
one. A wide `JITTER_DAYS` shift on a date near "today" in the source can jitter into the future.
Narrower than a fully general clamp: cap at the run's own captured "now".

## `AlterEgo.pattern(String)` has no `DirectIdStrategy` equivalent

**Type:** feature - **Importance:** low - **Effort:** medium
**Project:** incognito

No way for a policy to hand a custom shape pattern for a code-like column that isn't a
name/email/phone/etc. A `DirectIdStrategy.ALTEREGO_PATTERN` with a `pattern` field on
`ColumnPolicy` would close this - not blocked on `alterego`'s pattern-language extensions item,
though richer syntax there should be revisited here if it lands.

## `AlterEgo.creditCardNumber()` still unused by incognito

**Type:** feature - **Importance:** low - **Effort:** low
**Project:** incognito

Its practical gap was closed instead via `ColumnPolicy.redactionConstant` (a fixed placeholder,
arguably a better privacy story than per-row fabrication for a `SENSITIVE` field). Wiring the typed
generator itself remains possible but is no longer blocking anything.

## Publish generated Javadoc and link it in from the site

**Type:** feature - **Importance:** low - **Effort:** medium
**Project:** identigon.github.io

`alterego`/`incognito`'s Javadoc is built here but not published anywhere yet. Deliberately not
copied into `identigon.github.io` - exactly one source of truth for anything derived from the code
(`docs/adr/0034-identigon-github-io-as-a-separate-repository.md`) - needs a publishing step in
this repo's own CI, then a link added on the site once that exists.

## Per-subproject pages on the site

**Type:** docs - **Importance:** low - **Effort:** medium
**Project:** identigon.github.io

`alterego` / `incognito` / `effigies` each get only a one-line feature-grid blurb on the landing
page today. Worth a page each once there's more to say than that summary.

## Site search

**Type:** feature - **Importance:** low - **Effort:** low
**Project:** identigon.github.io

VitePress has built-in local search support; not worth turning on until there's enough content on
the site to search.

## Adopt Prettier alongside markdownlint-cli2 for `.md` files

**Type:** debt - **Importance:** low - **Effort:** medium

`markdownlint-cli2`'s MD013 (line length) has no auto-fix in any implementation - every rewrap
today is manual: edit, lint, find the violation, count columns, rewrap by hand, re-lint. Prettier's
`proseWrap: "always"` would automate that; it complements `markdownlint-cli2` rather than
replacing it (formatter vs linter - Prettier doesn't catch MD024/MD032/MD060 and friends).
`identigon.github.io` already runs it this way, scoped to `*.md` only. Could wire in the same
lightweight way `markdownlint-cli2` already is (a `pre-commit` hook - no `package.json`/
`node_modules` needed in this repo, matching today). Real cost before landing it: review the
one-time bulk-reformat diff across every existing `.md` file rather than rubber-stamp it, pin
whatever this repo's actual emphasis-marker convention (`*text*` vs `_text_`) already is into the
config rather than let Prettier silently change it, and write the ADR this kind of tooling
decision earns.
