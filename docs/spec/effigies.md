# Effigies - Specification

Effigies is a command-line application that **authors and orchestrates** an
[incognito](../../incognito) anonymisation run. It discovers a source schema, helps produce the
declarative policy that incognito consumes, and drives the engine to create the anonymised clone.
Effigies performs **no** value substitution and **no** relational orchestration itself - those
belong to incognito (and, beneath it, alterego).

This document is the authoritative behavioural contract. Where the code and this document disagree,
this document is intended to win - flag the gap rather than encode new behaviour. It is a skeleton
and will grow as the phases in `PLAN.md` land.

---

## 1. Purpose & boundary

Effigies exists so that using incognito does not require hand-writing Java. It turns "here is a
production database" into "here is a reviewed `policy.yaml`, and here is the anonymised clone lib-
incognito produced from it."

The engine below Effigies stays **deterministic and model-free**. All judgment - inferring what a
column is, scaffolding a starting policy, packaging a schema for an agent to reason about - lives in
Effigies. The reasoning for this split is in [ADR 23](../adr/0023-authoring-above-the-engine.md).

## 2. Hard invariants - never violate

These are the properties that make Effigies safe to point at a production database. They mirror and
respect incognito's own contract.

1. **Metadata only, never data.** Discovery and every artifact Effigies emits carry schema
   _metadata_ - table/column names, SQL types, the primary-/foreign-key graph, unique constraints.
   Effigies must **never** `SELECT` row values into a file, a log, or an artifact handed to a human
   or an agent. Authoring reasons about the schema, not the data.
2. **Fail-closed is preserved.** Effigies **suggests** roles; it never assigns one. A scaffolded
   policy leaves every column to be classified, and an unclassified column still aborts the
   incognito run (that engine's fail-closed contract). Effigies must not emit a "runnable" policy
   that silently defaults a column to a pass-through role.
3. **No model in the engine path.** Inference (heuristic or agent-driven) is authoring and produces
   a _draft_. The anonymisation run is a plain, reproducible incognito execution with no model call
   in it. The `policy.yaml` is the durable, reviewable artifact.
4. **Secrets stay out of the config.** A `policy.yaml` is meant to be reviewed and checked in.
   Database credentials and any fixed/`persistent` salt bytes are **secret material** and are
   supplied out-of-band (environment / secrets manager / flag), never written into the policy or any
   committed file.

## 3. Commands (intended contract)

The CLI is `java -jar identigon.jar <command> [options]`. `discover`, `scaffold`, `validate`, `run`,
`version`, and `help` are implemented; a bad or unknown invocation returns exit code 2. Every
subcommand also recognises `--help`/`-h` anywhere in its own arguments, printing its usage line and
exiting `0`.

- **`discover`** - inspect a source database and describe its schema (metadata only). Produces a
  human-readable summary and a machine-readable form for the later phases. Requires read access to
  the source's catalog; reads no row data. Reported column types are JDBC's own names
  (`java.sql.JDBCType`), not necessarily the database's own - e.g. PostgreSQL's `BOOLEAN` reports as
  `BIT`, and `TEXT` as `VARCHAR` - still a reliable input for choosing a strategy, just not
  identical to what the DDL says.
- **`scaffold`** - emit a starter `policy.yaml`: every discovered column listed, discovered metadata
  as comments (including the same JDBC type names `discover` reports), **every column left
  unclassified** (fail-closed). A draft, not a runnable config. Refuses to overwrite an existing
  output file unless `--force` is given.
- **`validate`** - check a `policy.yaml` against a source schema: the same fail-closed diagnostics
  `run` would raise, without a target connection or any data movement. Requires only source
  connection details (`--source-url`, `--source-user`, `IDENTIGON_SOURCE_PASSWORD`) - cheaper to
  iterate against while authoring, and usable as a CI pre-flight check for a policy going stale
  after a schema migration.
- **`run`** - execute incognito against a finished `policy.yaml` plus source/target connection
  details, producing the clone and surfacing the DPIA report (JSON / HTML / Markdown). The policy
  declares the salt **mode**; the salt **bytes** for fixed-salt modes come from out-of-band input.
  Refuses to start if any target table it would load into already has rows - a failed run's
  compensation deletes existing rows during clean-up, not only the ones this run itself inserted -
  unless `--force` is given.
- **`version`**, **`help`** - self-explanatory.

Exit codes: `0` success; `2` unknown command / bad usage; `3` a declared-but-unimplemented command.
(These will be refined as commands are implemented.)

## 4. Salt & reproducibility (delegated, surfaced)

Reproducibility is incognito's salt-mode choice, not a property of Effigies. A **deterministic
policy does not imply reproducible output** - the salt mode controls that, and the default
(ephemeral) is deliberately non-repeatable because it is the stronger anonymity posture:

- `ephemeral` (default) - fresh random salt per run; output differs each run; irreversible,
  unlinkable.
- `persistent` - a fixed reused salt; repeatable and linkable, but forfeits irreversibility.
- `reproducible` - fixed salt + seed; byte-for-byte reproducible, for test fixtures.

Effigies lets the policy declare the **mode** and injects the secret salt **bytes** (for the
fixed-salt modes) from out-of-band input (§2.4). Whichever mode is chosen, incognito's DPIA report
discloses it, so a reviewer can weigh the anonymity claim.

## 5. Relationship to incognito

Effigies **reuses** rather than re-implements: schema discovery via incognito's `SchemaInspector`,
the policy model + `YamlPolicyParser`, and the `IncognitoPipeline` execution + DPIA report. The one
capability that **migrated into** Effigies is role **inference** - incognito's own `PolicyInferrer`
and `autoInfer` concept, removed at incognito's 2.0: it was authoring, it never affected the
engine's output (fail-closed), and it belongs above the engine. Effigies' own `PolicyInferrer` is
the only one left.
