# Documentation map

What each document in this project is for, who reads it, how long it lives, and **where a given fact
belongs**. Start here when you have something to write down and are not sure which file it goes in.

Each artifact holds its own format and rules - in its preamble, or in its `0000-template.md` - and
this map does not restate them. Why the structure is shaped this way is in the adoption record,
`docs/decisions/0038-how-the-documentation-is-organised.md`.

## Where does it go?

The tense of the sentence you are writing usually settles it:

| If you are asking...                    | If you are writing...                                    | It belongs in                                                                             |
| --------------------------------------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| "what does it do? can I rely on X?"     | "the system does X"                                      | the specification                                                                         |
| "should it do X at all?"                | "X is out of scope" or "callers must not rely on X"      | the specification's scope, or what it leaves unspecified                                  |
| "why is it like this? was X tried?"     | "we chose X because Y", or "we chose not to do X"        | an ADR                                                                                    |
| "when did this change?"                 | "X used to be Y, now it is Z"                            | the changelog                                                                             |
| "is this known? is anyone fixing it?"   | "we should do X"                                         | the plan                                                                                  |
| "could this be faster, or smarter?"     | "X is worth doing once Y is true"                        | the plan, with Y named as the trigger                                                     |
| "is X tested?"                          | "X is deliberately not tested"                           | `docs/testing.md`                                                                         |
| "how do we know that?"                  | "source A says X, source B says Y, and A won because..." | a research note                                                                           |
| "why is this code like this?"           | "this code is like this because..."                      | a comment beside it - or an ADR the comment names, if the reason spans more than one site |
| "what did this code do before?"         | "this code used to..." or "fixed the bug where..."       | the commit message - never a comment                                                      |
| "is anything left to do here?"          | "this code should also..."                               | the plan - a `TODO` beside the code names the entry, never replaces it                    |
| "exactly what does the API accept?"     | "the specification says the API accepts X"               | the relevant `docs/spec/` member - never restated in a second document                    |
| "what must an agent know to work here?" | "an agent working here needs to know X"                  | `AGENTS.md`, if it is routing or a hazard - otherwise the document that owns the fact     |
| "where does this go?"                   | "this is how each document is used"                      | this file                                                                                 |

If a sentence seems to fit two places, it is usually two sentences. Split it and file each half.

The specification is a set, not a file: `docs/spec.md` indexes `docs/spec/`, one member per
subproject. This repository has three Gradle modules, each independently usable, but documentation
is consolidated at the repository root rather than split per module - see
`docs/decisions/0032-consolidate-documentation-at-repository-root.md`.

**This map also covers `identigon.github.io`**, the separate repository holding the project's public
site - its decisions live in `docs/decisions/` here and its backlog in `docs/plan.md` here (tagged
`identigon.github.io`), rather than in a second, separate doc-kit instance of its own. See
`docs/decisions/0033-extend-documentation-coverage-to-identigon-github-io.md`. That repository keeps
only its own `README.md` (orientation) and `AGENTS.md` (site-specific operational hazards - custom
domain, build/deploy); both route here for everything else, at the sibling paths
`../identigon.github.io/README.md` and `../identigon.github.io/AGENTS.md`.

## Layout

```text
DOC-MAP.md               this file - the map
README.md                orientation, one screen, links outward
AGENTS.md                orientation for coding agents - routes here
CLAUDE.md                one line pointing at AGENTS.md
CHANGELOG.md             what shipped
docs/
  spec.md                the specification index
  spec/
    alterego.md          the pseudonymisation library's contract
    incognito.md         the database-cloning/anonymisation engine's contract
    effigies.md          the authoring/orchestration CLI's contract
  plan.md                single ranked backlog, items tagged bug/debt/feature/docs
  plan/
    *.md                 optional per-entry working notes
    0000-template.md     optional task-note template
  decisions.md           index of decision records, generated
  decisions/
    *.md                 decisions, numbered, amendable by the owner
  research/
    *.md                 sourced findings with confidence levels
  testing.md             test strategy for all three subprojects, and what isn't covered
alterego/README.md       module orientation, links outward to the root docs/ above
incognito/README.md      module orientation, links outward to the root docs/ above
effigies/README.md       module orientation, links outward to the root docs/ above
quickstart/README.md     walkthrough for the quickstart worked example
alterego/tools/data-cache/SOURCES.md   provenance of cached upstream dictionary source data
incognito/src/test/resources/benchmarks/SOURCES.md  provenance/licensing of vendored benchmarks
.agents/skills/identigon-policy-author/SKILL.md  one agent tool skill, not project docs
../identigon.github.io/README.md   separate repository - orientation, an alias pointing back here
../identigon.github.io/AGENTS.md   separate repository - site-specific hazards, routes here
```

## Artifacts

| Artifact                                             | Purpose                                                                                                                                               | Tense      | Mutability         | Audience                     |
| ---------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- | ------------------ | ---------------------------- |
| `DOC-MAP.md`                                         | This map: what each document is for, and where a fact belongs                                                                                         | present    | rewritten in place | contributor                  |
| `README.md`                                          | Orients a newcomer in one screen, and links outward                                                                                                   | present    | rewritten in place | owner, contributor, consumer |
| `AGENTS.md`                                          | Behavioural rules for coding agents: build and test, hard invariants, code style and Git rules, plus the routing into this map. Facts live in the map | present    | rewritten in place | contributor (agents)         |
| `CLAUDE.md`                                          | **Alias** for `AGENTS.md`, existing only because some tools look for that filename                                                                    | present    | rewritten in place | contributor (agents)         |
| `docs/spec.md`                                       | The specification index: scope and the three members below. Holds no behaviour directly                                                               | present    | rewritten in place | consumer, contributor        |
| `docs/spec/alterego.md`                              | `alterego`'s full behavioural contract, including the normative Appendix A                                                                            | present    | rewritten in place | consumer, contributor        |
| `docs/spec/incognito.md`                             | `incognito`'s full behavioural contract, including the §7.3 invariants                                                                                | present    | rewritten in place | consumer, contributor        |
| `docs/spec/effigies.md`                              | `effigies`'s full behavioural contract                                                                                                                | present    | rewritten in place | consumer, contributor        |
| `CHANGELOG.md`                                       | What shipped, user-visible, across the whole monorepo. **Real standard:** Keep a Changelog, entries tagged by subproject                              | past       | append-only        | consumer                     |
| `docs/plan.md`                                       | Single ranked backlog. No standard; entries may carry a `Project` tag                                                                                 | future     | volatile           | owner, contributor           |
| `docs/decisions.md`                                  | Generated index of decision records. Only `accepted` records bind                                                                                     | present    | rewritten in place | owner, contributor           |
| `docs/decisions/*.md`                                | Why a decision was made, for any subproject or for `identigon.github.io`. **Real convention:** MADR minimal template, rules in `0000-template.md`     | past       | amendable          | owner, contributor           |
| `docs/research/*.md`                                 | Sourced findings behind a spec guarantee, with an explicit confidence level. **No standard**                                                          | past       | append-only        | contributor                  |
| `docs/testing.md`                                    | Test strategy for all three subprojects, and what is deliberately not covered                                                                         | present    | rewritten in place | contributor                  |
| `docs/plan/*.md`                                     | Working notes for one backlog item, prefixed with the subproject name when subproject-specific. Optional                                              | future     | disposable         | contributor                  |
| `docs/plan/0000-template.md`                         | **Template.** Task-note template. Optional                                                                                                            | present    | rewritten in place | contributor                  |
| `alterego/tools/data-cache/SOURCES.md`               | Provenance of cached upstream data used by the dictionary-curation tooling                                                                            | past       | append-only        | contributor                  |
| `quickstart/README.md`                               | **Alias** of `README.md` - step-by-step walkthrough for the quickstart worked example                                                                 | present    | rewritten in place | consumer, contributor        |
| `incognito/src/test/resources/benchmarks/SOURCES.md` | Provenance and licensing of the vendored benchmark-fixture schemas/datasets                                                                           | past       | append-only        | contributor                  |
| `.agents/skills/identigon-policy-author/SKILL.md`    | Step-by-step procedure for one agent tool skill                                                                                                       | imperative | rewritten in place | contributor (agents)         |
| `alterego/README.md`                                 | **Alias** of `README.md` - same purpose, scoped to this subproject                                                                                    | present    | rewritten in place | owner, contributor, consumer |
| `incognito/README.md`                                | **Alias** of `README.md` - same purpose, scoped to this subproject                                                                                    | present    | rewritten in place | owner, contributor, consumer |
| `effigies/README.md`                                 | **Alias** of `README.md` - same purpose, scoped to this subproject                                                                                    | present    | rewritten in place | owner, contributor, consumer |
| `../identigon.github.io/README.md`                   | **Alias** of `README.md` - same purpose, for the separate site repository                                                                             | present    | rewritten in place | owner, contributor, consumer |
| `../identigon.github.io/AGENTS.md`                   | Agent hazards specific to that repository (custom domain, build/deploy, Action pinning); routes here for decisions and backlog                        | present    | rewritten in place | contributor (agents)         |

## Lifecycle

| Artifact                                             | Created when                                    | Removed / closed when                                            |
| ---------------------------------------------------- | ----------------------------------------------- | ---------------------------------------------------------------- |
| `DOC-MAP.md`                                         | the structure is first agreed                   | never - revised when an artifact is added, removed or repurposed |
| `README.md`                                          | project starts                                  | never                                                            |
| `AGENTS.md`                                          | agents first work in this repository            | never                                                            |
| `CLAUDE.md`                                          | agents first work in this repository            | never                                                            |
| `docs/spec.md`                                       | behaviour is decided                            | never - edited forever                                           |
| `docs/spec/alterego.md`                              | behaviour is decided                            | never - edited forever                                           |
| `docs/spec/incognito.md`                             | behaviour is decided                            | never - edited forever                                           |
| `docs/spec/effigies.md`                              | behaviour is decided                            | never - edited forever                                           |
| `CHANGELOG.md` entry                                 | at release, if user-visible                     | never                                                            |
| `docs/plan.md` entry                                 | idea occurs - one paragraph, no design          | **deleted** when done, not struck through                        |
| `docs/decisions.md`                                  | first decision is recorded                      | never                                                            |
| `docs/decisions/*.md`                                | a choice a newcomer would question              | deprecated or deleted, with the owner's permission               |
| `docs/research/*.md`                                 | a question is investigated                      | never - confidence is revised in place as evidence changes       |
| `docs/testing.md`                                    | the second test approach appears (already true) | never                                                            |
| `docs/plan/*.md`                                     | work begins on an item                          | work completes                                                   |
| `docs/plan/0000-template.md`                         | the directory is adopted                        | when the directory is removed                                    |
| `alterego/tools/data-cache/SOURCES.md`               | the data cache is populated                     | never                                                            |
| `quickstart/README.md`                               | the example is created                          | never                                                            |
| `incognito/src/test/resources/benchmarks/SOURCES.md` | a benchmark fixture is vendored                 | never                                                            |
| `.agents/skills/identigon-policy-author/SKILL.md`    | the skill is written                            | the skill is retired                                             |
| `alterego/README.md`                                 | the module is created                           | never                                                            |
| `incognito/README.md`                                | the module is created                           | never                                                            |
| `effigies/README.md`                                 | the module is created                           | never                                                            |
| `../identigon.github.io/README.md`                   | the site repository is created                  | never                                                            |
| `../identigon.github.io/AGENTS.md`                   | the site repository is created                  | never                                                            |

## Flow

A change moves through the documents in this order:

`docs/plan.md` entry -> (optional `docs/plan/` note) -> **ADR** if a real choice was made ->
regenerate **`docs/decisions.md`** -> **`docs/spec.md`** or its member updated in present tense ->
**`CHANGELOG.md`** line if user-visible -> `docs/plan.md` entry **deleted**.

Most changes skip the ADR and the task note. Nothing skips the deletion.

## Rules

1. **Facts have one home.** A table, a list of more than three values, or a process of three or more
   steps should appear once; short instructional copies and template/instance pairs may repeat.
2. **Tense routes it:** rationale -> ADR, behaviour -> specification, history -> changelog or commit
   message, intent -> plan.
3. **Prose links to a machine-readable contract and never restates it.** It covers only what the
   contract cannot express: rationale, invariants, units, ownership, policy.
4. **A generated view is never hand-edited.** It carries a header naming what generates it, and
   regenerating it produces no diff. An ordered, immutable-once-applied sequence - migrations - is
   changelog-shaped; the specification is the current shape.
5. **Write the least that is true.** No history or account of how you got there, in documents or
   code comments.
6. **Code names the record, not the reverse.** A comment beside constrained code names the ADR or
   quirk that governs it. Only quirk entries name code, in their `Where:` field.
7. **Cite a decision record as `ADR-NNNN`**, and another repository's record by a link. A citation
   says as little about the record as it can, and is fixed in the same commit as a change to the
   record that makes it untrue.

## Deliberately not here

Absent on purpose, so that adding any of them later is a decision rather than a drift:

- **A separate technical-debt file** - debt is a `debt` tag in the plan.
- **An issue tracker.** The backlog is deliberately kept as one file, despite 51 open entries, above
  the kit's rough 20-30 threshold; the single ranked list is the point, and moving it to a tracker
  is separate work. See `docs/decisions/0038-how-the-documentation-is-organised.md`.
- **Community health files** - `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md` - until people
  other than the owner contribute regularly.
- **`RELEASING.md`.** The release ritual is in
  `docs/decisions/0028-publish-effigies-runnable-jar.md` and `.github/workflows/release.yml`; a
  second guide would restate them. **Runbooks** are absent because this is not a deployed service.
- **Per-subproject `DOC-MAP.md`, `docs/spec.md`, `docs/plan.md`, `docs/decisions/` or
  `CHANGELOG.md`.** See `docs/decisions/0032-consolidate-documentation-at-repository-root.md`.
- **A separate `DOC-MAP.md`/`docs/plan.md`/`docs/decisions/` instance inside
  `identigon.github.io`.** See
  `docs/decisions/0033-extend-documentation-coverage-to-identigon-github-io.md`.
- **`docs/decisions/README.md`.** The generated index lives at `docs/decisions.md`, outside the
  record directory, so the MADR-shape check does not sweep it.
- **`docs/quirks.md`, `docs/glossary.md`.** Not adopted; each specification member states known
  limitations and non-goals inline. Revisit if that stops being enough.
- **`docs/howto/`, `docs/sources/`.** No recurring operational procedures or externally pinned
  specifications yet; the quickstart README covers the one walkthrough.
- **`docs/archive/`.** Nothing has yet had its currency called into question strongly enough to
  archive rather than fix or delete.

## Adding a new kind of document

Before adding one, check it has a **distinct tense, mutability and audience** from everything in the
artifacts table. If it shares all three with an existing document, it is a section or a tag within
that document, unless it holds different facts. The checker warns about a shared triple; it cannot
tell whether the facts differ.

The one exception is an **alias**: a file whose entire content points at another artifact, existing
only because a tool or reader looks for that exact path - a module `README.md`, a `CLAUDE.md`
pointing at `AGENTS.md`. Mark it `**Alias**` in its purpose cell, give it the properties of what it
points at, and give it nothing of its own to say.

If it passes, add it to the layout, artifacts and lifecycle tables in the same commit. A map that
omits an artifact is worse than no map, because it is believed.
