# Effigies — Implementation Plan

Phased plan for **Effigies**, the authoring/orchestration CLI above
[incognito](../incognito). See `SPECIFICATION.md` for the
behavioural contract and `docs/adr/0001-authoring-above-the-engine.md` for the boundary that shapes
everything below.

**Scope (locked):** Effigies never anonymises anything itself — it *discovers* a schema, *authors*
(and *infers*) an incognito policy, and *drives* the engine. The engine stays deterministic and
model-free; every judgment lives here. Effigies reads schema **metadata only**, never row data, and
never assigns a column role behind the user's back (fail-closed is incognito's, and is
preserved).

**Build prerequisite:** `effigies` depends on `incognito` (and, transitively, `alterego`) as sibling
Gradle subprojects (`project(":incognito")` — see the monorepo root `settings.gradle.kts`), so
`./gradlew build` at the repo root builds all three in dependency order automatically; no separate
publish/resolve step is needed for local development.

Effigies moves to a 2.0.x incognito once that lands (which removes the inference migrating here —
see Phase 3 and the ADR).

---

## Phase 0: Project skeleton

- [x] Gradle (Kotlin DSL) build with a Java 25 toolchain; `application` plugin; a single runnable
  ("fat") jar so the tool runs via `java -jar build/libs/effigies.jar`.
- [x] Code hygiene in step with the sibling repos: Spotless (tidy-only), SpotBugs + find-sec-bugs,
  `.pre-commit-config.yaml` (spotless + compile + gitleaks + native hooks), and a CI workflow that
  builds alterego and incognito to Maven local first, then builds Effigies.
- [x] `EffigiesCli` dispatch stub: `discover` / `scaffold` / `run` declared (return "not yet
  implemented"), plus `help` / `version`; covered by `EffigiesCliTest`.
- [x] Base docs: `README.md`, `SPECIFICATION.md`, this plan, `CHANGELOG.md`, `docs/adr/` (with ADR
  0001).

## Phase 1: Schema discovery (`discover`)

- [x] Connect to a source database (connection details from CLI args / env / a config file — never a
  committed secret) and inspect its schema by **reusing incognito's `SchemaInspector`** (tables,
  columns, PKs, FKs, unique indexes, SQL types). Metadata only — no `SELECT` of row data.
- [x] Emit a human-readable schema summary and a machine-readable form (the shape the later phases
  and an agent consume).
- [x] Decide connection-config format and where credentials come from (documented: env/secret, not
  the config file).

## Phase 2: Policy scaffold (`scaffold`)

- [x] Emit a starter `policy.yaml` listing every discovered table/column, with the discovered
  type/PK/FK metadata as comments, and **every column left unclassified** so a run still fails
  closed until a human (or Phase 3) fills it in. The scaffold is a *draft*, never a runnable config.

## Phase 3: Inference (migrated from incognito)

- [x] Move the role-inference heuristics (`PolicyInferrer` and the `autoInfer` concept) out of
  incognito into Effigies — inference is authoring, and because incognito is fail-closed it
  never affected execution, so the move is behaviour-neutral for the engine (ADR 0001). This pairs
  with incognito's 2.0 removal of that API.
- [x] Pre-fill the scaffold's suggestions, **clearly marked as suggestions**, never auto-applied.

## Phase 4: Orchestration (`run`)

- [x] Given a finished `policy.yaml` plus source/target connection details, drive incognito
  (`YamlPolicyParser` → `IncognitoPipeline`) to produce the clone, and surface the DPIA report
  (JSON/HTML/Markdown) it emits.
- [x] Salt handling per `SPECIFICATION.md`: the config declares the salt **mode** (`ephemeral`
  default / `persistent` / `reproducible`); the secret salt **bytes** for the fixed-salt modes come
  from out-of-band input (env/secret/flag), never the config. The DPIA report already discloses the
  chosen mode.

## Phase 5: Interactive Agent Skill for Policy Authoring

- [x] Build an interactive AI "Agent Skill" (compatible with standard agent platforms like Claude,
  Antigravity, Copilot) to replace the static artifact approach. The agent reads the scaffolded
  `policy.yaml`, cross-references it with schema metadata and the deterministic Phase 3 inference
  suggestions, and interactively interviews the user to fill in unclassified columns.
- [x] **Risk Mitigation — User Fatigue:** The skill must aggressively batch related questions (e.g.,
  grouping standard audit timestamps across multiple tables) rather than interrogating the user
  column-by-column, preventing users from blindly confirming out of boredom.
- [x] **Risk Mitigation — Context Limits:** The skill must employ a paginated or topological
  (table-by-table) workflow to avoid context window exhaustion and hallucinated relationships when
  processing massive enterprise schemas.
- [x] The skill must honor the fail-closed philosophy: it surfaces insights and asks targeted
  questions, but never silently assigns a role without user confirmation. It iterates until the
  policy is valid.

---

## Code hygiene tooling

- [x] Spotless (tidy-only), SpotBugs + find-sec-bugs (CI, `ignoreFailures = false`), pre-commit
  hooks, gitleaks — mirroring incognito.
- [x] **PMD — done.** Shares the root `config/pmd/ruleset.xml` with `alterego`/`incognito`.
  `ignoreFailures = false`, wired into `check`. Only cosmetic findings here (a `StringBuilder`
  under-sized for what it accumulates); the CLI's `args[++i]` flag-parsing idiom and known-single-row
  `rs.next()` lookups are covered by the shared ruleset's existing ignore list, not a per-project
  exception.
- [x] **Spotless/SpotBugs/PMD config consolidated to the monorepo root — done.** Mirrors incognito:
  SpotBugs's `exclude.xml` moved to `config/spotbugs/exclude-effigies.xml` at the root; Spotless's
  `java { }` block, SpotBugs's `toolVersion`/`ignoreFailures`/report shape, and PMD's whole block
  now live once in the root `build.gradle.kts`'s `subprojects { }` instead of being copy-pasted per
  subproject. The `excludeFilter` path is the only SpotBugs setting that stays in this subproject's
  own `build.gradle.kts` — its suppressions are deliberately not unioned with alterego's/incognito's.
  The find-sec-bugs plugin dependency moved into the same root guard too. JUnit BOM brought up to
  `6.1.3` (was `5.10.2`).
- [ ] Optional / consistency-only: JaCoCo.

## Post-v1.0 — possible future directions

- [ ] A non-interactive "authoring session" mode that runs discover → scaffold → (agent) → run in
  one invocation, with the DPIA report fed back for iteration.
- [ ] Support for engines incognito adds beyond PostgreSQL, without change here.
