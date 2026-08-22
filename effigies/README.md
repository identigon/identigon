# Effigies

Effigies is a Java 25 command-line application that sits **above** the
[incognito](../incognito) anonymisation engine. It is the
*authoring and orchestration* layer: it inspects a source database's schema, helps you author (and,
later, infer) the declarative anonymisation policy, and then drives incognito to produce the
schema-identical, PII-free clone.

The name: an *effigy* is a deliberately fake likeness. Effigies makes the likeness; incognito
carries out the substitution.

> **In one sentence:** *"Point me at a production database and help me produce a reviewable
> configuration that incognito can run to make an anonymised copy — without me
> hand-writing Java, and without ever showing a real data value to anything that doesn't need it."*

## Where it sits (and what stays out)

Three layers, each with one job:

| Layer | Responsibility | Deterministic? | Judgment / LLM? |
|:------|:---------------|:---------------|:----------------|
| [alterego](../alterego) | fabricate one field value | yes | no |
| [incognito](../incognito) | clone the schema + orchestrate the load from a policy | yes | no |
| **Effigies (this directory)** | discover schema, author/infer the policy, drive a run | authoring is advisory; the produced config + runs are deterministic | **yes — here only** |

Two boundaries are deliberate and load-bearing:

- **No model in the engine path.** Any inference — heuristic or agent-driven — is *authoring*. The
  anonymisation itself stays a deterministic, reproducible, model-free incognito run. The policy
  YAML is the durable, checked-in, reviewable artifact; Effigies helps you write it, then gets out
  of the way.
- **Fail-closed survives.** Effigies never assigns a column role behind your back. It *suggests*; an
  unclassified column still aborts the run (incognito's fail-closed contract, ADR 0004 there).
  The DPIA report incognito emits — source-value survival, misdeclaration lint, structural
  findings, and the illustrative sample rows — is the safety net that catches a bad classification.
- **Metadata only.** Schema discovery and any artifact Effigies produces for a human or an agent
  carry schema *metadata* (names, types, the FK graph) — never sampled real values. Authoring works
  from the schema, not the data.

See [`docs/adr/0001-authoring-above-the-engine.md`](docs/adr/0001-authoring-above-the-engine.md) for
the reasoning, [`docs/spec/effigies.md`](../docs/spec/effigies.md) for the behavioural contract, and
[`PLAN.md`](PLAN.md) for the phased plan.

## Status

**v1.0 Complete.** The core authoring workflow is fully implemented.

## Try it in five minutes

[`examples/quickstart/`](examples/quickstart/) is a small, self-contained PostgreSQL schema (no
Docker, no third-party data) with a finished `policy.yaml` — the fastest way to see
`discover` → `scaffold` → `run` and the DPIA report working end to end before pointing Identigon
at a real database.

## Usage Workflow

The typical workflow uses three CLI commands and an AI Agent Skill.

### 1. Discover & Scaffold
Inspect the source database to emit a starter (fail-closed) `policy.yaml`:
```bash
# Read the source schema (metadata only)
export IDENTIGON_SOURCE_PASSWORD="secret"
java -jar build/libs/identigon.jar discover --source-url "jdbc:postgresql://..." --source-user "admin"

# Generate a starter policy.yaml with deterministic heuristics (Phase 3)
java -jar build/libs/identigon.jar scaffold --source-url "jdbc:postgresql://..." --source-user "admin" --out ./policy.draft.yaml
```

### 2. Interactive Policy Authoring (The Agent Skill)
Effigies ships with a built-in Agent Skill for AI assistants (Claude, Antigravity, Copilot, etc.)
that interactively interviews you to safely classify the remaining columns.

Activate the skill in your agent (located at
`.agents/skills/identigon-policy-author/SKILL.md`). The agent will:
- Read your scaffolded `policy.yaml`.
- Batch related columns (e.g., all audit timestamps) to prevent fatigue.
- Ask for your explicit confirmation before applying roles (maintaining the fail-closed guarantee).

### 3. Orchestration (`run`)
Once the policy is authored, drive the `incognito` engine to produce the clone:
```bash
export IDENTIGON_SOURCE_PASSWORD="secret"
export IDENTIGON_TARGET_PASSWORD="secret"

# Required for persistent/reproducible salt modes (configured in policy.yaml)
export IDENTIGON_SALT="my-secret-salt-bytes"

java -jar build/libs/identigon.jar run \
  --policy ./policy.yaml \
  --source-url "jdbc:postgresql://..." --source-user "admin" \
  --target-url "jdbc:postgresql://..." --target-user "admin"
```
The engine will execute the pipeline and surface the DPIA accountability report as
`dpia-report.html` (presentation-ready), `dpia-report.json` (machine-readable), and
`dpia-report.md` (human-diffable).

## Build & run

`./gradlew build` (from the monorepo root) produces a single runnable jar; run it with a bare
`java -jar`:

```sh
java -jar build/libs/identigon.jar help
```

(or `./gradlew run --args="help"` during development).

## Licence

MIT — see the [root LICENCE](../LICENCE).
