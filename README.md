# Identigon

Identigon anonymises databases: it clones a production database into a schema-identical copy with
every piece of personal data replaced by clearly fictional data, and gives you the tools to author
that anonymisation without hand-writing code. Three Java 25 subprojects form a pipeline, each usable
and documented independently:

| Subproject | What it is | Depends on |
|:---|:---|:---|
| [`alterego/`](alterego) | A zero-dependency library for deterministic pseudonymisation — replaces individual values (names, dates, identifiers) with realistic fictional substitutes. | — |
| [`incognito/`](incognito) | A library that clones a database and orchestrates the anonymisation: schema discovery, load ordering, key translation, coherent cross-entity relationships. Delegates all field-value fabrication to `alterego`. | `alterego` |
| [`effigies/`](effigies) | A CLI that discovers a schema, authors (and helps infer) the declarative policy `incognito` runs from, and drives the engine to produce the clone. | `incognito` |

Each subproject's behavioural contract lives under root [`docs/spec/`](docs/spec/); each still has
its own `PLAN.md` (implementation plan and backlog) and `docs/adr/` (design decisions) — see the
subproject's own README for what it does and how to use it, and
[`docs/tasks/consolidate-subproject-docs.md`](docs/tasks/consolidate-subproject-docs.md) for the
ongoing migration to a fully repository-wide documentation set. Release history is split at the
point the three merged: each subproject's own `CHANGELOG.md` covers everything before 1.0.0, and the
root [`CHANGELOG.md`](CHANGELOG.md) covers every release from 1.0.0 onward.

## Building

All three subprojects are wired together as one Gradle multi-project build. From this directory:

```sh
./gradlew build
```

builds and tests all three, in dependency order (`alterego` → `incognito` → `effigies`). Scope any
task to a single subproject with `:name:`, e.g. `./gradlew :incognito:test`.

## Versioning

The three subprojects version together, not independently — a version bump anywhere in the monorepo
bumps the shared number for all three. See any subproject's "lockstep versioning" ADR
([`alterego`](alterego/docs/adr/0015-lockstep-versioning.md),
[`incognito`](incognito/docs/adr/0008-lockstep-versioning.md),
[`effigies`](effigies/docs/adr/0002-lockstep-versioning.md)) for why.

## Licence

MIT overall — see [`LICENCE`](LICENCE). `alterego/` carries an additional Open Government Licence
attribution for its bundled UK dictionary data; see [`alterego/LICENCE`](alterego/LICENCE) and
[`alterego/NOTICE`](alterego/NOTICE).
