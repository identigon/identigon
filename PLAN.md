# Identigon — Implementation Plan

Single ranked backlog for the whole monorepo. Entries are **deleted** when done, never annotated —
a plan that accumulates completed items stops being read. Optionally tagged `**Project:**`
(`alterego` / `incognito` / `effigies`) for work scoped to one subproject; an untagged entry is
cross-cutting or applies to no single subproject. See the root `CHANGELOG.md` for what's already
shipped.

## Outstanding

- [ ] **Consolidate subproject docs into root docs.** `SPECIFICATION.md` and `PLAN.md` are done;
  `alterego`, `incognito`, and `effigies` still each have their own `docs/adr/` and `CHANGELOG.md`,
  migrating to a single repo-wide documentation set (justified by lockstep versioning — one
  version, one release, for all three). See `docs/tasks/consolidate-subproject-docs.md` for the
  phased plan.
- [ ] **Consider turning on more of `markdownlint-cli2`'s rule set, not just line length.**
  `.markdownlint-cli2.jsonc` currently enables only `MD013` (`default: false`) — deliberately
  narrow, matching what was actually asked for when the hook was added. Checked read-only with
  the full default rule set enabled (`default: true`, same MD013 overrides): 279 violations.
  `MD040` fenced-code-language (22) and `MD034` no-bare-urls (21) were straightforward — every
  bare fence got a `text`/`sh` language tag, every bare citation URL wrapped in `<...>` — fixed
  directly, not tracked here. What's left is real editorial judgment, not a mechanical fix:
  `MD060` table-column-style (140 — every table would need padding/alignment reformatting),
  `MD007` ul-indent (56), plus ~40 more across
  `MD022`/`MD031`/`MD032`/`MD024`/`MD041`/`MD036`/`MD012`/`MD033` (blank-line, heading, and
  duplicate-heading conventions). Picking which (if any) of these to enable is a separate
  decision, not bundled here.
  Also worth setting `MD013.strict: true` at the same time as any widening: the default
  (non-strict) mode is more lenient than "100 columns, no exceptions" — it forgives a trailing
  word that starts within the limit but ends past it, so a line can run slightly over 100 and
  still pass (found by testing, not by reading the docs). `strict` removes that slack but also
  removes the automatic long-URL/link-only-line exemption, so switching would need the 8
  already-identified long-URL lines individually suppressed (`<!-- markdownlint-disable-line
  MD013 -->`) rather than passing implicitly.

- [ ] **Project:** incognito — **Composite PK + cyclic FK together.** Currently fails closed with a
  clear message rather than corrupting data; not exercised by any benchmark. Bigger than "widen the
  pass-2 `UPDATE` to key on every PK column" — the real gap is deferred *composite-FK* resolution
  into a cyclic table (a composite-PK table can only be in a cycle if a composite FK references it,
  which hits a separate guard first). See
  `docs/tasks/incognito-composite-pk-cyclic-fk.md` for the full analysis and handoff.
- [ ] **Project:** alterego — **`ServiceLoader`-based strategy/dictionary packs for additional
  countries.** Post-v1 extensibility: distribute new-country dictionaries and strategies as
  separate artifacts rather than bundling every country in core.
- [ ] **Project:** alterego — **Language-sensitive generation.** v1 built-ins resolve entirely by
  the locale's country; the language component steers nothing yet. Unused so far — no concrete
  need has surfaced.
- [ ] **Project:** alterego — **Pattern-language extensions: character classes and repetition
  counts.** `pattern(String)`'s `D`/`L`/`l`/`A` mini-language has no `[ABC]` character-class or
  `D{5}` repetition syntax. A genuine specification contract change (new syntax, new exception
  cases, new conformance tests) — do deliberately, not as a drive-by. `incognito`'s planned
  `ALTEREGO_PATTERN` strategy doesn't need this to land first (today's syntax is already enough to
  wire) but should be revisited to expose any richer syntax added here.
- [ ] **Project:** alterego — **External `MappingStore` modules (JDBC, Redis).** Build against the
  existing contract test once a real need appears; a local file-backed store already ships in core
  (see the file-backed-mapping-store ADR).
- [ ] **Project:** alterego — **A public codec SPI for caller-supplied value types.** The fixed
  value-type set was deliberately rejected as a public SPI (see the fixed-value-type-set ADR);
  revisit only if a real need appears.
- [ ] **Project:** alterego — **Fictional-range additions: TEST-NET IP addresses (RFC 5737).** Same
  fictional-by-default family as the existing built-ins' reserved ranges.
- [ ] **Project:** alterego — **`companyNumber()` (Companies House) — blocked, unsolved fictional
  space.** No reserved/test range and no checksum exist for UK company numbers; the only
  structurally-impossible value is zero, and mapping every company to zero is redaction, not
  pseudonymisation. A high range is time-dependent (Scotland is already at `SC770005`). Deferred
  until a reserved or never-issued range is found — full analysis in
  `alterego/docs/fictional-ranges.md`. Has a regional element (`SC`/`NI`/plain) that would feed
  `UK_NATION` record coherence if it returns.
- [ ] **Project:** incognito — **Remove `PolicyInferrer` and
  `AnonymisationPolicy.Builder.autoInfer(boolean)` (committed, next major).** Both are
  `@Deprecated(forRemoval = true)`. Inference is authoring, not execution — fail-closed means it
  never affected engine output — and the maintained version now lives in `effigies`' own
  `PolicyInferrer`; this copy only survives for the fail-closed error message's diagnostic hint.
  See `effigies/docs/adr/0001-authoring-above-the-engine.md` for the reasoning and
  `effigies/docs/adr/0002-lockstep-versioning.md` for the version-bump mechanics.
- [ ] **Project:** incognito — **Multi-edge structural fingerprints.** The shipped
  structural-uniqueness report scores one FK edge at a time; combining several edges into one joint
  fingerprint per subject is more faithful to real singling-out but harder to threshold defensibly.
- [ ] **Project:** incognito — **Attribute + structure combined findings.** A kept
  `distinguishing: false` value plus a rare FK fan-out is a stronger fingerprint than either alone;
  not modelled today.
- [ ] **Project:** incognito — **Declarative-partitioning support.** The Pagila benchmark excludes
  the partitioned `payment` table because partition children are discovered as plain tables with no
  special handling. A proper treatment recognises the parent/child relationship
  (`pg_partitioned_table`/`pg_inherits`), clones by inserting into the parent (letting Postgres
  route to partitions), and skips the children — preserving per-partition volumes.
- [ ] **Project:** incognito — **`RedisKeyTranslationStore` — a persisted, out-of-process key
  store.** v1.0 ships only an in-memory store (Redis is an explicit v1.0 non-goal). Would let key
  translation outlive a single JVM run — useful for very large clones, resuming an interrupted
  load, and cross-run surrogate stability. A persisted key store is itself sensitive and must be
  destroyed on successful completion, exactly as the salt is.
- [ ] **Project:** incognito — **No UK bank-account generator in `alterego`.** Unlike the other UK
  identifiers, there's no primitive to wire even if `DirectIdStrategy` grew a case for it. Surfaced
  authoring a bank-account column for the effigies quickstart example, which falls back to
  `ALTEREGO_GENERIC` (no fictionality guarantee) in the meantime.
- [ ] **Project:** incognito — **Temporal `QUASI_ID` jitter never touches the time-of-day
  component.** Every temporal strategy zeroes seconds or does date-only arithmetic; a `TIMESTAMP`
  column's clock time always survives exactly as-is. `alterego` already supports jittering it
  (`TimeField.HOUR`, an explicit range, a seconds half-range, `shiftInstant()`) — none of it
  reachable from a policy today.
- [ ] **Project:** incognito — **No way to clamp a jittered `QUASI_ID` date/time to "not in the
  future."** `alterego`'s `JitterOptions.max(...)` already supports an inclusive upper bound (the
  caller-supplied-bound principle — see the explicit-clamp-bounds ADR), but `incognito` never
  passes one. A wide `JITTER_DAYS` shift on a date near "today" in the source can jitter into the
  future. Narrower than a fully general clamp: cap at the run's own captured "now".
- [ ] **Project:** incognito — **`AlterEgo.pattern(String)` has no `DirectIdStrategy` equivalent.**
  No way for a policy to hand a custom shape pattern for a code-like column that isn't a
  name/email/phone/etc. A `DirectIdStrategy.ALTEREGO_PATTERN` with a `pattern` field on
  `ColumnPolicy` would close this — not blocked on `alterego`'s pattern-language extensions item,
  though richer syntax there should be revisited here if it lands.
- [ ] **Project:** incognito — **`AlterEgo.creditCardNumber()` still unused by incognito (low
  priority).** Its practical gap was closed instead via `ColumnPolicy.redactionConstant` (a fixed
  placeholder, arguably a better privacy story than per-row fabrication for a `SENSITIVE` field).
  Wiring the typed generator itself remains possible but is no longer blocking anything.
- [ ] **Project:** effigies — **JaCoCo.** Optional, consistency-only with `alterego`/`incognito`'s
  coverage reporting.
- [ ] **Project:** effigies — **A non-interactive "authoring session" mode.** Runs discover →
  scaffold → (agent) → run in one invocation, with the DPIA report fed back for iteration.
- [ ] **Project:** effigies — **Support for engines `incognito` adds beyond PostgreSQL.** No change
  needed here when it lands.
- [ ] **Project:** effigies — **Revisit `examples/quickstart/` and the Agent Skill once
  `incognito`'s policy-API backlog lands.** Several `alterego` capabilities `incognito` doesn't
  expose yet (remaining identifier generators, a bank-account generator, `RecordScope` cross-field
  coherence, jitter/clamp knobs, a `pattern(String)` strategy — see the matching
  `incognito`-tagged entries above) are candidates to fold into the quickstart policy and teach the
  `identigon-policy-author` skill to suggest, as each lands.
