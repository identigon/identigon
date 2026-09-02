---
status: "accepted"
date: 2026-08-11
decision-makers: David Conneely
---

# 34. `identigon.github.io` as a separate repository, not a subdirectory of this one

## Context and Problem Statement

Identigon needed a public-facing site - primarily a landing/marketing page with room to grow into
guide content and, eventually, links to generated reference material. Where that site's source
lives (inside this monorepo, or a repository of its own) determines the URL it can serve from.

## Considered Options

- A subdirectory of this repository (e.g. a `site/` folder), built and deployed from the monorepo.
- A dedicated `identigon.github.io` repository - GitHub Pages' special naming convention for an
  organisation's root site.

## Decision Outcome

Chosen option: "a dedicated `identigon.github.io` repository", because the special
`<org>.github.io` name is the only way to get the clean root URL (`identigon.org` rather than a
`/identigon/`-suffixed path), which was the deciding factor for a public front door. Generated
reference material (Javadoc etc.) is deliberately not duplicated there - it is built and published
from this repository's own CI and linked to, keeping exactly one source of truth for anything
derived from the code.

### Consequences

- Good, because the site serves from the clean root URL (`identigon.org`), the deciding
  requirement for a public front door.
- Good, because generated reference material stays a link, not a copy - built once, in this
  repository's own CI, with no risk of a stale duplicate drifting out of sync on the site.
- Bad, because it is a second repository to maintain - its own `.gitattributes`, pre-commit
  config, branch protection - on a deploy cadence fully decoupled from the monorepo's release
  cadence, in a different toolchain (Node/VitePress) that the monorepo's Gradle CI has no reason
  to know about.
- Neutral: `identigon.github.io`'s own documentation - its decisions, its backlog - now lives in
  this repository instead (see ADR 33), so "separate repository" describes code/deploy separation,
  not documentation separation.

<!-- Migrated from identigon.github.io's own README.md "## Decisions" section (dated entry,
     2026-08-11) during the doc-kit coverage-extension migration, ADR 33. -->
