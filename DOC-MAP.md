# Documentation map

This is the map. It describes what each document in this project is for, who reads it, how long it
lives, and — the question it exists to answer — **where a given fact belongs**.

Start here when you have something to write down and are not sure which file it goes in.

## Where does it go?

The tense of the sentence you are writing usually settles it:

| If you are writing… | It belongs in |
| --- | --- |
| "`alterego`/`incognito`/`effigies` does X" | the specification (`docs/spec/<subproject>.md`) |
| "we chose X because Y" | an ADR |
| "X used to be Y, now it is Z" | the changelog |
| "we should do X" | the plan |
| "this is how each document is used" | this file |

If a sentence seems to fit two places, it is usually two sentences. Split it and file each half.

## Layout

```text
DOC-MAP.md                 this file — the map
AGENTS.md                  behavioural rules for agents; carries the pointer into this map
CLAUDE.md                  a pointer to AGENTS.md, for tools that look for this name specifically
README.md                  repository orientation, one screen, links outward
SPECIFICATION.md           index into the docs/spec/ tree below
CHANGELOG.md               what shipped, across the whole monorepo (lockstep releases from 1.0.0;
                            project-prefixed entries for each subproject's pre-lockstep history)
PLAN.md                    single ranked backlog, entries optionally tagged with a Project
docs/
  adr/*.md                  decisions about any subproject's design, numbered across the whole
                             monorepo — a decision belongs to whichever subproject it constrains,
                             not to a per-subproject sequence
  tasks/*.md                optional: per-item working notes, disposable; prefixed with the
                             subproject name where the work is subproject-specific
docs/spec/
  alterego.md                the pseudonymisation library's contract
  incognito.md                the database-cloning/anonymisation engine's contract
  effigies.md                 the authoring/orchestration CLI's contract
alterego/README.md         module orientation, links outward to the root docs/ above
incognito/README.md        module orientation, links outward to the root docs/ above
effigies/README.md         module orientation, links outward to the root docs/ above
effigies/.agents/skills/identigon-policy-author/SKILL.md  one agent tool skill, not project
                                                            documentation
```

This repository has three Gradle modules, each independently usable and documented, but they
**version and release together** (lockstep — see the lockstep-versioning ADR). That single shared
release history is why documentation is consolidated at the repository level rather than split per
module (doc-kit's usual monorepo default): a module boundary that does not correspond to an
independent release cadence is not a boundary the documentation needs to keep either. See
`docs/tasks/consolidate-subproject-docs.md` for the migration this consolidation is being carried
out under, and its current phase.

**Transitional note.** As of this map's most recent revision, `docs/spec/` is consolidated but
`docs/adr/`, `PLAN.md`, and `CHANGELOG.md` are still mid-migration: `alterego/`, `incognito/`, and
`effigies/` each still carry their own `docs/adr/`, `PLAN.md`, and `CHANGELOG.md` until the
remaining phases in `docs/tasks/consolidate-subproject-docs.md` land. This map states the target
shape; where it and the working tree disagree, the task file's phase checklist is authoritative
about what has actually moved.

## Artifacts

| Artifact | Purpose | Tense | Durability | Audience |
| --- | --- | --- | --- | --- |
| `DOC-MAP.md` | This map: what each document is for, and where a fact belongs | present | rewritten in place | anyone adding documentation |
| `README.md` | Orient a newcomer fast at the repository root | present | rewritten in place | anyone |
| `<subproject>/README.md` | **Alias** of `README.md` — same purpose, scoped to that subproject | present | rewritten in place | anyone |
| `SPECIFICATION.md` | Index over the `docs/spec/` tree beside it. **No standard for the file** | present | rewritten in place | anyone adding a behaviour fact |
| `docs/spec/alterego.md` | `alterego`'s full behavioural contract | present | rewritten in place | consumers of `alterego`; `incognito` implementers |
| `docs/spec/incognito.md` | `incognito`'s full behavioural contract | present | rewritten in place | consumers of `incognito`; `effigies` implementers |
| `docs/spec/effigies.md` | `effigies`'s full behavioural contract | present | rewritten in place | CLI users |
| `docs/adr/*.md` | Why a decision was made, for any subproject. **Real convention:** [MADR](https://adr.github.io/madr/) minimal template | past | immutable once accepted | future maintainers |
| `CHANGELOG.md` | What shipped, user-visible, across the whole monorepo. **Real standard:** [Keep a Changelog](https://keepachangelog.com), adapted: grouped by subproject per release, not by change-type category | past | append-only | users |
| `PLAN.md` | Single ranked backlog. **No standard**; entries may carry a `Project` tag | future | volatile | the team |
| `docs/tasks/*.md` | Working notes for one backlog item, prefixed with the subproject name when subproject-specific. **No standard** | future | disposable | whoever picks it up |
| `AGENTS.md` | Behavioural rules for coding agents, including the pointer into this map. **Real convention:** [agents.md](https://agents.md) | present | rewritten in place | coding agents |
| `CLAUDE.md` | **Alias** of `AGENTS.md` — some tools look for this filename specifically; the file itself is just a pointer | present | rewritten in place | coding agents |
| `effigies/.agents/skills/identigon-policy-author/SKILL.md` | Step-by-step procedure for one agent tool skill | imperative | rewritten in place | coding agents |

## Lifecycle

| Artifact | Created when | Removed / closed when |
| --- | --- | --- |
| `DOC-MAP.md` | the structure is first agreed | never — revised when an artifact is added, removed or repurposed |
| `README.md` | project starts | never |
| `<subproject>/README.md` | the module is created | never |
| `SPECIFICATION.md` | behaviour is decided | never — edited forever |
| `docs/spec/*.md` | behaviour is decided | never — edited forever |
| `docs/adr/*.md` | a choice a newcomer would question | never — status flips to `superseded by ADR-NNNN` |
| `CHANGELOG.md` entry | at release, if user-visible | never |
| `PLAN.md` entry | idea occurs — one paragraph, no design | **deleted** when done, not struck through |
| `docs/tasks/*.md` | work begins on an item | work completes |
| `AGENTS.md` | agents first work in this repository | never |
| `CLAUDE.md` | agents first work in this repository | never |

## Flow

A change moves through the documents in this order:

`PLAN.md` entry → (optional `docs/tasks/` note) → **ADR** if a real choice was made →
**`SPECIFICATION.md`** member updated in present tense → **`CHANGELOG.md`** line if user-visible →
`PLAN.md` entry **deleted**.

Most changes skip the ADR. Nothing skips the deletion.

## Prescribed formats

**ADR** — one file per decision, numbered across the whole monorepo (`0001-short-title.md`,
continuing past the current highest number regardless of which subproject the next decision
concerns), following the [MADR](https://adr.github.io/madr/) minimal template — YAML front matter
(`status`, `date`, `decision-makers`), `Considered Options` as its own section. Copy
`docs/adr/0000-template.md` for the shape once it exists (see the migration task file).

- Status is one of `proposed`, `rejected`, `accepted`, `deprecated`, `superseded by ADR-NNNN`.
- **Only `accepted` binds.**
- **Immutable once accepted**, except to change status, date, and decision-makers, or a
  formatting-only edit that changes no word.
- **Changing a status is a human action.**

**Changelog** — reverse-chronological, an `Unreleased` section at the top. Each version groups
changes **by subproject** (a subsection per subproject that actually changed that release), not by
change-type category — the monorepo adaptation of Keep a Changelog. Pre-lockstep history from each
subproject's own former `CHANGELOG.md` is folded in with a project-prefixed version tag
(`alterego-0.1.0`, `incognito-1.0.0`, `effigies-1.0.0`, …) to avoid colliding with the shared
lockstep version numbers, which start fresh at `1.0.0`.

**Plan entry** — a checkbox, a bold title, then one paragraph. Optionally prefixed with a
`**Project:** alterego|incognito|effigies` tag; cross-cutting or unknown-project entries carry no
tag. No design; anything longer needs an ADR. Entries are deleted when done, never annotated.

## Deliberately not here

- **Per-subproject `DOC-MAP.md`, `SPECIFICATION.md`, `PLAN.md`, `docs/adr/`, or `CHANGELOG.md`.**
  Consolidated to the repository root because the subprojects release in lockstep — see "Layout"
  above and the lockstep-versioning ADR.
- **`docs/quirks.md`, `docs/glossary.md`, `docs/testing.md`.** Not yet adopted; each subproject's
  own `SPECIFICATION.md` member currently states known limitations and non-goals inline. Revisit if
  that stops being enough.
- **`docs/archive/`.** Nothing has yet had its currency called into question strongly enough to
  archive rather than fix or delete.

## Adding a new kind of document

Before adding one, check it has a **distinct tense, mutability and audience** from everything in the
artifacts table. If it shares all three with an existing document, it is a section or a tag within
that document, not a new file.

If it passes, add it to both tables here in the same commit. A map that omits an artifact is worse
than no map, because it is believed.
