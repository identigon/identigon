# Effigies — Specification

Effigies is a command-line application that **authors and orchestrates** a
[lib-incognito](https://github.com/identigon/lib-incognito) anonymisation run. It discovers a source
schema, helps produce the declarative policy that lib-incognito consumes, and drives the engine to
create the anonymised clone. Effigies performs **no** value substitution and **no** relational
orchestration itself — those belong to lib-incognito (and, beneath it, lib-alterego).

This document is the authoritative behavioural contract. Where the code and this document disagree,
this document is intended to win — flag the gap rather than encode new behaviour. It is a skeleton
and will grow as the phases in `PLAN.md` land.

---

## 1. Purpose & boundary

Effigies exists so that using lib-incognito does not require hand-writing Java. It turns "here is a
production database" into "here is a reviewed `policy.yaml`, and here is the anonymised clone lib-
incognito produced from it."

The engine below Effigies stays **deterministic and model-free**. All judgment — inferring what a
column is, scaffolding a starting policy, packaging a schema for an agent to reason about — lives in
Effigies. The reasoning for this split is in
[`docs/adr/0001-authoring-above-the-engine.md`](docs/adr/0001-authoring-above-the-engine.md).

## 2. Hard invariants — never violate

These are the properties that make Effigies safe to point at a production database. They mirror and
respect lib-incognito's own contract.

1. **Metadata only, never data.** Discovery and every artifact Effigies emits carry schema
   *metadata* — table/column names, SQL types, the primary-/foreign-key graph, unique constraints.
   Effigies must **never** `SELECT` row values into a file, a log, or an artifact handed to a human
   or an agent. Authoring reasons about the schema, not the data.
2. **Fail-closed is preserved.** Effigies **suggests** roles; it never assigns one. A scaffolded
   policy leaves every column to be classified, and an unclassified column still aborts the
   lib-incognito run (that engine's fail-closed contract). Effigies must not emit a "runnable"
   policy that silently defaults a column to a pass-through role.
3. **No model in the engine path.** Inference (heuristic or agent-driven) is authoring and produces
   a *draft*. The anonymisation run is a plain, reproducible lib-incognito execution with no model
   call in it. The `policy.yaml` is the durable, reviewable artifact.
4. **Secrets stay out of the config.** A `policy.yaml` is meant to be reviewed and checked in.
   Database credentials and any fixed/`persistent` salt bytes are **secret material** and are
   supplied out-of-band (environment / secrets manager / flag), never written into the policy or any
   committed file.

## 3. Commands (intended contract)

The CLI is `java -jar effigies.jar <command> [options]`. `discover`, `scaffold`, `run`, `version`,
and `help` are implemented; a bad or unknown invocation returns exit code 2.

- **`discover`** — inspect a source database and describe its schema (metadata only). Produces a
  human-readable summary and a machine-readable form for the later phases. Requires read access to
  the source's catalog; reads no row data.
- **`scaffold`** — emit a starter `policy.yaml`: every discovered column listed, discovered metadata
  as comments, **every column left unclassified** (fail-closed). A draft, not a runnable config.
- **`run`** — execute lib-incognito against a finished `policy.yaml` plus source/target connection
  details, producing the clone and surfacing the DPIA report (JSON / HTML / Markdown). The policy
  declares the salt **mode**; the salt **bytes** for fixed-salt modes come from out-of-band input.
- **`version`**, **`help`** — self-explanatory.

Exit codes: `0` success; `2` unknown command / bad usage; `3` a declared-but-unimplemented command.
(These will be refined as commands are implemented.)

## 4. Salt & reproducibility (delegated, surfaced)

Reproducibility is lib-incognito's salt-mode choice, not a property of Effigies. A **deterministic
policy does not imply reproducible output** — the salt mode controls that, and the default
(ephemeral) is deliberately non-repeatable because it is the stronger anonymity posture:

- `ephemeral` (default) — fresh random salt per run; output differs each run; irreversible,
  unlinkable.
- `persistent` — a fixed reused salt; repeatable and linkable, but forfeits irreversibility.
- `reproducible` — fixed salt + seed; byte-for-byte reproducible, for test fixtures.

Effigies lets the policy declare the **mode** and injects the secret salt **bytes** (for the
fixed-salt modes) from out-of-band input (§2.4). Whichever mode is chosen, lib-incognito's DPIA
report discloses it, so a reviewer can weigh the anonymity claim.

## 5. Relationship to lib-incognito

Effigies **reuses** rather than re-implements: schema discovery via lib-incognito's
`SchemaInspector`, the policy model + `YamlPolicyParser`, and the `IncognitoPipeline` execution +
DPIA report. The one capability that **migrates into** Effigies is role **inference** (currently
`PolicyInferrer` / the `autoInfer` concept in lib-incognito): it is authoring, it never affected the
engine's output (fail-closed), and it belongs above the engine. That migration pairs with
lib-incognito's 2.0.
