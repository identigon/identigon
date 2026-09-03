---
status: "accepted"
date: 2026-09-02
decision-makers: David Conneely
---

# 32. Consolidate documentation at the repository root, not per subproject

## Context and Problem Statement

Identigon adopted a doc-kit-style documentation structure (`DOC-MAP.md`, `SPECIFICATION.md`,
`PLAN.md`, `docs/adr/`, `docs/research/`, `docs/testing.md`) across a monorepo of three Gradle
subprojects (`alterego`, `incognito`, `effigies`), each independently usable and independently
publishable (`alterego`/`incognito` to GitHub Packages).

That structure's own default for a monorepo is per-module: each subproject gets its own
specification, its own ADR sequence, its own plan, with only the map, `README.md`, `CHANGELOG.md`
and `PLAN.md` staying at the repository root. Identigon's actual layout puts all of these at the
root instead - `docs/spec/` (indexed by root `SPECIFICATION.md`), `docs/adr/`, `docs/research/`, and
`docs/testing.md` are shared, not per-subproject - and that choice was never recorded on its own
terms. It followed from ADR 24 (lockstep versioning) in practice, but that record settles the
version-_number_ question specifically; it does not, by itself, settle where documentation lives.

## Considered Options

- Per-module documentation - the structure's own monorepo default: each subproject gets its own
  `docs/spec/`, `docs/adr/`, `docs/research/`, `docs/testing.md`, and `PLAN.md`.
- Consolidated at the repository root - one shared `docs/adr/` (numbered across all three
  subprojects), one `docs/research/`, one `docs/testing.md`, one `PLAN.md`, one `CHANGELOG.md`; only
  the specification splits per subproject, as an index (`SPECIFICATION.md`) over `docs/spec/`
  members, because each subproject's behavioural contract is genuinely a separate thing even though
  they release together.

## Decision Outcome

Chosen option: "consolidated at the repository root", because the three subprojects release in
lockstep (ADR 24) - one version, one tag, one `CHANGELOG.md` entry per release - so there is no
independent release cadence for per-module documentation to track in the first place, and because a
decision or a change frequently spans the subproject boundary: `effigies`' authoring flow depends
directly on `incognito`'s policy API, and `incognito`'s own specification carries an
`alterego`-integration cheat-sheet mirroring `alterego`'s public surface. A per-module split would
force many real decisions to be recorded twice, once per affected subproject, or awkwardly
cross-referenced between separate ADR sequences.

The one exception is the specification itself: `docs/spec/alterego.md`, `docs/spec/incognito.md` and
`docs/spec/effigies.md` remain three separate members, each the sole place its subproject's
behavioural contract belongs, indexed by root `SPECIFICATION.md`. That split is not a per-module
documentation structure in the sense this decision rejects - it is the ordinary "specification
outgrows one file, becomes an index plus a tree" shape, applied because each subproject genuinely
has its own contract, not because of any module boundary consideration.

### Consequences

- Good, because the root-only artifact set (`DOC-MAP.md`, `README.md`, `CHANGELOG.md`, `PLAN.md`)
  that this structure prescribes for the _whole_ repository in a per-module layout becomes, in
  effect, the artifact set for _everything_ here - one `docs/adr/` sequence, one `PLAN.md`, one
  `docs/testing.md` - rather than three parallel copies that would need to stay mutually consistent
  by hand.
- Good, because a cross-subproject decision (most of `alterego`'s ADRs shape a primitive `incognito`
  goes on to use; `effigies`' authoring decisions depend on what `incognito`'s policy API exposes)
  has exactly one natural home instead of a choice about which subproject's sequence it belongs to,
  or two records that have to agree with each other.
- Bad, because a contributor working entirely inside one subproject's directory has to look up to
  root `docs/spec/<subproject>.md` rather than finding it inside that subproject's own tree -
  mitigated by each subproject's own `README.md` (an Alias, per `DOC-MAP.md`) linking directly to
  its specification member.
- Neutral: this is motivated by the same fact as ADR 24 (a strictly linear dependency chain with a
  single shared release), but is a distinct choice from it - a monorepo could lockstep-version while
  still splitting documentation per module, or the reverse. This record is what specifically settles
  the documentation question; ADR 24 settles only the version-number one.
