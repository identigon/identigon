# Identigon

Identigon anonymises databases: it clones a production database into a schema-identical copy with
every piece of personal data replaced by clearly fictional data, and gives you the tools to author
that anonymisation without hand-writing code. Three Java 25 subprojects form a pipeline, each
independently usable, sharing one repository-wide documentation set:

| Subproject                | What it is                                                                                                                                                                                                       | Depends on  |
| :------------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------- |
| [`alterego/`](alterego)   | A zero-dependency library for deterministic pseudonymisation - replaces individual values (names, dates, identifiers) with realistic fictional substitutes.                                                      | -           |
| [`incognito/`](incognito) | A library that clones a database and orchestrates the anonymisation: schema discovery, load ordering, key translation, coherent cross-entity relationships. Delegates all field-value fabrication to `alterego`. | `alterego`  |
| [`effigies/`](effigies)   | A CLI that discovers a schema, authors (and helps infer) the declarative policy `incognito` runs from, and drives the engine to produce the clone.                                                               | `incognito` |

Each subproject's behavioural contract lives under root [`docs/spec/`](docs/spec/); design decisions
for all three are in root [`docs/adr/`](docs/adr/), numbered across the whole monorepo; the single
root [`PLAN.md`](PLAN.md) tracks the whole backlog (optionally tagged by subproject) - see
[`DOC-MAP.md`](DOC-MAP.md) for the full map, and the subproject's own README for what it does and
how to use it. Release history is split at the point the three merged: the root
[`CHANGELOG.md`](CHANGELOG.md) covers pre-1.0.0 history too (project-prefixed version tags), as well
as every shared release from 1.0.0 onward.

## Try it in five minutes

[`quickstart/`](quickstart/) is a small, self-contained PostgreSQL schema (no third-party data) with
a finished `policy.yaml` - the fastest way to see `discover` -> `scaffold` -> `run` and the DPIA
report working end to end, without Docker for the schema itself or a real production database.
Requires Docker (for a throwaway Postgres container) and Java 25:

```sh
cd quickstart && ./run-quickstart.sh
```

(Windows without a POSIX shell: `.\run-quickstart.ps1` instead.) See
[`quickstart/README.md`](quickstart/README.md) for the real authoring workflow - scaffolding a draft
and classifying it with the `identigon-policy-author` Agent Skill, rather than using the finished
policy this one-shot demo skips straight to.

## Building

All three subprojects are wired together as one Gradle multi-project build. From this directory:

```sh
./gradlew build
```

builds and tests all three, in dependency order (`alterego` -> `incognito` -> `effigies`). Scope any
task to a single subproject with `:name:`, e.g. `./gradlew :incognito:test`.

## Versioning

The three subprojects version together, not independently - a version bump anywhere in the monorepo
bumps the shared number for all three. See the
["lockstep versioning" ADR](docs/adr/0024-lockstep-versioning.md) for why.

## Licence

MIT overall - see [`LICENCE`](LICENCE). `alterego/` carries an additional Open Government Licence
attribution for its bundled UK dictionary data; see [`alterego/LICENCE`](alterego/LICENCE) and
[`alterego/NOTICE`](alterego/NOTICE).
