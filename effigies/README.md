# Effigies

Effigies is a Java 25 command-line application that sits **above** the
[lib-incognito](https://github.com/identigon/lib-incognito) anonymisation engine. It is the *authoring
and orchestration* layer: it inspects a source database's schema, helps you author (and, later, infer)
the declarative anonymisation policy, and then drives lib-incognito to produce the schema-identical,
PII-free clone.

The name: an *effigy* is a deliberately fake likeness. Effigies makes the likeness; lib-incognito
carries out the substitution.

> **In one sentence:** *"Point me at a production database and help me produce a reviewable
> configuration that lib-incognito can run to make an anonymised copy — without me hand-writing Java,
> and without ever showing a real data value to anything that doesn't need it."*

## Where it sits (and what stays out)

Three layers, each with one job:

| Layer | Responsibility | Deterministic? | Judgment / LLM? |
|:------|:---------------|:---------------|:----------------|
| [lib-alterego](https://github.com/identigon/lib-alterego) | fabricate one field value | yes | no |
| [lib-incognito](https://github.com/identigon/lib-incognito) | clone the schema + orchestrate the load from a policy | yes | no |
| **Effigies (this repo)** | discover schema, author/infer the policy, drive a run | authoring is advisory; the produced config + runs are deterministic | **yes — here only** |

Two boundaries are deliberate and load-bearing:

- **No model in the engine path.** Any inference — heuristic or agent-driven — is *authoring*. The
  anonymisation itself stays a deterministic, reproducible, model-free lib-incognito run. The policy
  YAML is the durable, checked-in, reviewable artifact; Effigies helps you write it, then gets out of
  the way.
- **Fail-closed survives.** Effigies never assigns a column role behind your back. It *suggests*; an
  unclassified column still aborts the run (lib-incognito's fail-closed contract, ADR 0004 there). The
  DPIA report lib-incognito emits — source-value survival, misdeclaration lint, structural findings,
  and the illustrative sample rows — is the safety net that catches a bad classification.
- **Metadata only.** Schema discovery and any artifact Effigies produces for a human or an agent carry
  schema *metadata* (names, types, the FK graph) — never sampled real values. Authoring works from the
  schema, not the data.

See [`docs/adr/0001-authoring-above-the-engine.md`](docs/adr/0001-authoring-above-the-engine.md) for
the reasoning, [`SPECIFICATION.md`](SPECIFICATION.md) for the behavioural contract, and
[`PLAN.md`](PLAN.md) for the phased plan.

## Status

**Skeleton.** The CLI dispatches its planned subcommands (`discover`, `scaffold`, `run`) but they are
not yet implemented. See `PLAN.md`.

## Build & run

Effigies depends on lib-incognito, which depends on lib-alterego; neither is published to a public
repository yet, so build them into your local Maven repo first (upstream-first):

```
cd ../lib-alterego  && ./gradlew publishToMavenLocal
cd ../lib-incognito && ./gradlew publishToMavenLocal
cd ../app-effigies  && ./gradlew build
```

`./gradlew build` produces a single runnable jar; run it with a bare `java -jar`:

```
java -jar build/libs/app-effigies.jar help
```

(or `./gradlew run --args="help"` during development).

## Licence

MIT — see [`LICENCE`](LICENCE).
