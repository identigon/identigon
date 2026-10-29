---
status: "proposed"
date: 2026-10-29
decision-makers: {the owner - required once the status is not "proposed"}
approval: {the owner's words, verbatim - required once the status is not "proposed"}
---

# 38. How the documentation is organised

## Context and Problem Statement

The repository has a pre-0.5.0 doc-kit shape: `SPECIFICATION.md` indexes `docs/spec/`, `PLAN.md`
holds the backlog, decisions live in `docs/adr/`, and task notes live in `docs/tasks/`. doc-kit
0.5.0 moved those paths to `docs/spec.md`, `docs/plan.md`, `docs/decisions/` and `docs/plan/`, made
the map's property sets closed, and made accepted decision records amendable by the owner. The old
map and templates no longer match the kit's current structure, and the old `AGENTS.md` rule that
accepted records are immutable conflicts with the new record rules.

The structure comes from doc-kit at commit `5312a15` (v0.6.0). Its decision records, which give the
reasons for each rule, are at `https://github.com/dconneely/doc-kit/tree/5312a15/docs/decisions`.

## Considered Options

- Adopt doc-kit 0.6.0's structure, preserving this repository's documented deviations.
- Keep the pre-0.5.0 structure and the old map, plan, specification and record rules.
- Adopt 0.6.0 and flatten the repository's custom structure into the kit's monorepo default.

## Decision Outcome

Chosen option: "Adopt doc-kit 0.6.0's structure, preserving this repository's documented
deviations", because repeated agent sessions need one map that matches the files on disk and one set
of current record rules, while the repository's three-module documentation and specification tree
are recorded in ADR-0032, its site coverage in ADR-0033, and its lockstep release model in ADR-0024.

Deviations from the kit:

- Documentation stays consolidated at the repository root for the three Gradle modules, rather than
  split per module (ADR-0032).
- The specification stays a tree at `docs/spec/`, indexed by `docs/spec.md`, with one member per
  subproject (ADR-0032).
- The separate `identigon.github.io` repository (ADR-0034) keeps its decisions and backlog here
  (ADR-0033), and its `README.md` and `AGENTS.md` are named by the sibling paths
  `../identigon.github.io/README.md` and `../identigon.github.io/AGENTS.md`.
- The changelog keeps its per-subproject entry tags and its pre-lockstep, project-prefixed version
  sections.
- The plan stays one 51-entry file, above the kit's rough 20-30 issue-tracker threshold; the single
  ranked list is deliberate.
- Task notes stay under `docs/plan/`.
- Existing record titles name their answers rather than their questions; they are kept unchanged
  during the migration.
- Historical provenance HTML comments in records are kept and citation-corrected; they are not
  removed as the template's guidance comments are.
- The moves were ordinary file moves, not `git mv`, because Git operations belong to the owner; the
  commit records them as renames.
- The generated decision index is `docs/decisions.md`, outside the record directory.
- The 36 accepted records keep their historical `date` values; their `approval` field records the
  owner's migration permission, `Migrate all 37`, rather than a new status change.

### Consequences

- Good, because every fact has one home, and `DOC-MAP.md` says which.
- Good, because the map's tense, mutability and audience values are a closed set, so near-duplicates
  cannot drift in.
- Good, because accepted records are current answers that can be amended, rather than immutable
  documents that force a second record for every correction.
- Good, because the paths match doc-kit 0.6.0, so its checker and index generator apply directly.
- Bad, because the map must name every documentation file, and someone has to keep it so.
- Bad, because the site repository's README and AGENTS still link to the old paths until that
  repository is changed separately.
- Neutral: the decision index is generated, so it must be regenerated when a record changes.

## Confirmation

`sh tools/doc-kit-check.sh` passes from the repository root.
