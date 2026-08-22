---
status: "accepted"
date: 2026-08-09
decision-makers: David Conneely
---

# 23. Authoring (and inference) lives in Effigies, above a deterministic engine

## Context and Problem Statement

Using incognito requires a fully-classified anonymisation policy: every column assigned a role and
strategy, or the run fails closed (ADR 17). Producing that policy is real work — inspecting the
schema, judging what each column is, writing YAML — and it is exactly the kind of judgment that is
tedious for a human and well-suited to heuristics or an LLM/agent.

There is pressure to push that judgment *down* into the engine: auto-infer roles, let a model
classify columns, "just make it work" from a connection string. incognito already carries a small
piece of this (`PolicyInferrer` and an `autoInfer` flag) — but, by its fail-closed design, inference
there only ever *suggests*; it never assigns a role, so it has no effect on what the engine
produces. It is a half-exposed authoring feature sitting inside an execution engine.

Two properties must not be compromised: the anonymisation run must stay **deterministic and
reproducible** (a privacy tool cannot depend on a model call to anonymise), and it must stay
**fail-closed** (nothing classifies a column silently). Meanwhile the authoring judgment wants room
to evolve — regex heuristics today, an agent tomorrow.

## Considered Options

* Push classification judgment (heuristics, model inference) further into the engine itself.
* Split responsibilities across a fixed boundary: the engine stays deterministic and
  judgment-free; all authoring (including inference) lives above it, in a separate tool.

## Decision Outcome

Chosen option: "split responsibilities across a fixed boundary", because it lets authoring evolve
freely without ever touching the engine's determinism or fail-closed guarantees, and keeps a model
(if used) confined to *authoring* a config, never to *running* one.

* **The engine (incognito) stays deterministic, model-free, and judgment-free.** It discovers the
  schema (it must, to execute), validates a *finished* policy fail-closed, orchestrates the load,
  and emits the DPIA report. No inference, no defaults-that-guess.
* **All authoring lives in Effigies, above the engine.** Effigies *reuses* the engine's schema
  discovery and policy model, and *owns* everything judgment-shaped: inference, scaffolding, and
  the artifact handed to an agent. Its output is a reviewed `policy.yaml`; the run itself is a
  plain incognito execution.
* **Inference migrates out of incognito into Effigies.** Because inference there was
  execution-inert (fail-closed meant it never assigned a role), moving it changes zero engine
  behaviour. This pairs with incognito's 2.0, which removes that public API.

Corollary constraints (recorded in `docs/spec/effigies.md` §2): Effigies reads schema **metadata
only, never row data**; it never emits a runnable policy that silently defaults a column; and
secrets (credentials, fixed-salt bytes) are injected out-of-band, never written into the policy.

### Consequences

* Good, because the engine's guarantee is clean and defensible: it makes no judgment about what is
  sensitive; a human (aided by Effigies) declares it, and the DPIA report's survival/lint/structural
  findings and sample rows are the safety net that catches a wrong declaration.
* Good, because authoring can evolve freely — regex → agent → hybrid — without ever touching the
  deterministic engine or its reproducibility/fail-closed guarantees.
* Neutral: a model may participate in *authoring* a config, but never in *running* one; the
  `policy.yaml` is the durable, reviewable boundary between the two.
* Bad, because it costs a second artifact (Effigies) to build and version, and a breaking change to
  incognito (the removal of `PolicyInferrer` / `autoInfer`), taken as its 2.0. Accepted
  deliberately.
