---
status: "accepted"
date: 2026-09-02
decision-makers: David Conneely
---

# 33. Extend this repo's documentation coverage to `identigon.github.io`

## Context and Problem Statement

`identigon.github.io` is a small, separate repository - the public-facing site for the Identigon
project, built with VitePress (see ADR 34 for why it's a separate repository at all, and ADR 35
for the choice of VitePress). It never adopted this repo's doc-kit structure on its own -
correctly, per doc-kit's own size threshold, since its entire documentable surface is a handful of
hand-written guide pages plus two real decisions and a short backlog, well under where a full
separate `DOC-MAP.md`/`PLAN.md`/`docs/adr/` instance would earn its keep. But those real decisions
and that backlog didn't disappear - they ended up as informal prose in that repo's own
`README.md` (`## Decisions`, `## Roadmap` sections), exactly the "rationale in the wrong tense, in
the wrong file" pattern doc-kit's own migration guide flags as the highest-value split to make -
identical to what ADR 32 already corrected for this repo's own subproject documentation.
`identigon.github.io`'s own `AGENTS.md` already names this tension explicitly ("however
doc-kit-shaped a `docs/` subtree might look").

## Considered Options

* Leave `identigon.github.io`'s decisions and backlog as informal README prose indefinitely -
  accept the tension its own `AGENTS.md` already names, on the reasoning that the repo is too
  small to earn a doc-kit structure of its own.
* Stand up a full, separate doc-kit instance inside `identigon.github.io` (its own `DOC-MAP.md`,
  `PLAN.md`, `docs/adr/`) - conformant, but a second parallel copy of the same rules for a
  repository that, on its own, is well under doc-kit's own size threshold.
* Extend this repo's existing, already-consolidated documentation structure to cover
  `identigon.github.io` too - the site repo's decisions land in this repo's `docs/adr/`, its
  backlog in this repo's `PLAN.md` (tagged), the same way the three Gradle subprojects'
  documentation was already consolidated here rather than split three ways (ADR 32).

## Decision Outcome

Chosen option: "extend this repo's existing structure", because `identigon.github.io`'s own real
decisions and backlog genuinely belong to the same project - its own `README.md`/`AGENTS.md`
already treat "the code" and "the site" as one product, split across two repos purely for the
clean root-URL requirement (ADR 34) - the same reasoning ADR 32 already applied to the three
Gradle subprojects, one repository over. A full second doc-kit instance would fail doc-kit's own
size test on `identigon.github.io`'s actual documentable surface: two decisions, three backlog
items, no specification-shaped contract, no release history of its own.

`identigon.github.io` keeps only what any repository genuinely needs locally, regardless of where
its decisions/plan live: its own `README.md` (orientation, links out) and its own `AGENTS.md`
(routes to this repo's `DOC-MAP.md` for everything cross-repo; keeps only the hazards that only
make sense standing inside that repo - the custom-domain footguns, the build/deploy commands, the
Action-pinning gotcha). This is the same shape `alterego`/`incognito`/`effigies`' own `README.md`
Aliases already use.

### Consequences

* Good, because `identigon.github.io`'s genuine decisions and backlog get a real home (`docs/adr/`,
  `PLAN.md`) instead of indefinitely living as under-specified README prose, without standing up a
  second parallel doc-kit instance that would fail this repo's own size test.
* Good, because a decision already made about the site (its VitePress choice, ADR 35) and a future
  decision about, say, publishing generated Javadoc from this repo's own CI and linking it in from
  the site, sit in the same ADR sequence as the decision on the CI side - no ambiguity about which
  of two separate sequences a cross-repo decision belongs in.
* Bad, because a contributor working purely inside `identigon.github.io` has to look at a different
  repository for the map, the backlog, and the decision record, not their own repo's root -
  mitigated the same way `alterego`/`incognito`/`effigies`' own `README.md` Aliases already are:
  `identigon.github.io`'s own `README.md` and `AGENTS.md` link directly to the specific artifacts
  here.
* Neutral: this does not fold `identigon.github.io`'s own operational git history, build tooling,
  or deploy workflow into this repo - only its documentation (decisions, backlog). The two remain
  separate repositories, separate deploy cadences, separate toolchains; only where "why" and
  "what's next" for the site get recorded changes.
