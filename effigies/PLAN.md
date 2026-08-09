# Effigies — Implementation Plan

Phased plan for **Effigies**, the authoring/orchestration CLI above
[lib-incognito](https://github.com/identigon/lib-incognito). See `SPECIFICATION.md` for the
behavioural contract and `docs/adr/0001-authoring-above-the-engine.md` for the boundary that shapes
everything below.

**Scope (locked):** Effigies never anonymises anything itself — it *discovers* a schema, *authors*
(and *infers*) a lib-incognito policy, and *drives* the engine. The engine stays deterministic and
model-free; every judgment lives here. Effigies reads schema **metadata only**, never row data, and
never assigns a column role behind the user's back (fail-closed is lib-incognito's, and is
preserved).

**Build prerequisite:** lib-incognito (and, transitively, lib-alterego) are consumed from the local
Maven repo until they are published to a shared repository. Build them first, upstream-first:

```
cd ../lib-alterego  && ./gradlew publishToMavenLocal
cd ../lib-incognito && ./gradlew publishToMavenLocal
```

Effigies currently pins `org.identigon:incognito:1.1.0-SNAPSHOT`; it moves to `2.0.x` once
lib-incognito 2.0 lands (which removes the inference migrating here — see Phase 3 and the ADR).

---

## Phase 0: Project skeleton

- [x] Gradle (Kotlin DSL) build with a Java 25 toolchain; `application` plugin; a single runnable
  ("fat") jar so the tool runs via `java -jar build/libs/effigies.jar`.
- [x] Code hygiene in step with the sibling repos: Spotless (tidy-only), SpotBugs + find-sec-bugs,
  `.pre-commit-config.yaml` (spotless + compile + gitleaks + native hooks), and a CI workflow that
  builds lib-alterego and lib-incognito to Maven local first, then builds Effigies.
- [x] `EffigiesCli` dispatch stub: `discover` / `scaffold` / `run` declared (return "not yet
  implemented"), plus `help` / `version`; covered by `EffigiesCliTest`.
- [x] Base docs: `README.md`, `SPECIFICATION.md`, this plan, `CHANGELOG.md`, `docs/adr/` (with ADR
  0001), `docs/tasks/`.

## Phase 1: Schema discovery (`discover`)

- [x] Connect to a source database (connection details from CLI args / env / a config file — never a
  committed secret) and inspect its schema by **reusing lib-incognito's `SchemaInspector`** (tables,
  columns, PKs, FKs, unique indexes, SQL types). Metadata only — no `SELECT` of row data.
- [x] Emit a human-readable schema summary and a machine-readable form (the shape the later phases
  and an agent consume). See `docs/tasks/001-schema-discovery-and-scaffold-yaml.md`.
- [x] Decide connection-config format and where credentials come from (documented: env/secret, not
  the config file).

## Phase 2: Policy scaffold (`scaffold`)

- [x] Emit a starter `policy.yaml` listing every discovered table/column, with the discovered
  type/PK/FK metadata as comments, and **every column left unclassified** so a run still fails
  closed until a human (or Phase 3) fills it in. The scaffold is a *draft*, never a runnable config.

## Phase 3: Inference (migrated from lib-incognito)

- [x] Move the role-inference heuristics (`PolicyInferrer` and the `autoInfer` concept) out of
  lib-incognito into Effigies — inference is authoring, and because lib-incognito is fail-closed it
  never affected execution, so the move is behaviour-neutral for the engine (ADR 0001). This pairs
  with lib-incognito's 2.0 removal of that API.
- [x] Pre-fill the scaffold's suggestions, **clearly marked as suggestions**, never auto-applied.

## Phase 4: Orchestration (`run`)

- [x] Given a finished `policy.yaml` plus source/target connection details, drive lib-incognito
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
  hooks, gitleaks — mirroring lib-incognito.
- [ ] Optional / consistency-only: PMD, JaCoCo.

## Post-v1.0 — possible future directions

- [ ] A non-interactive "authoring session" mode that runs discover → scaffold → (agent) → run in
  one invocation, with the DPIA report fed back for iteration.
- [ ] Support for engines lib-incognito adds beyond PostgreSQL, without change here.
