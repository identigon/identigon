---
status: "accepted"
date: 2026-08-11
decision-makers: David Conneely
---

# 35. VitePress as the `identigon.github.io` site generator

## Context and Problem Statement

The site needs to grow from a single landing page into hand-written guide content without becoming
hard to maintain, which rules out a purely ad hoc approach once there is more than a couple of
pages.

## Considered Options

- Plain static HTML/CSS - no templating once there is more than a couple of pages.
- Jekyll - GitHub Pages' native option, zero-config, no Actions workflow needed, but Ruby-based and
  increasingly dated.
- VitePress - Markdown-first, a built-in "home" layout, a reasonable out-of-the-box docs theme.

## Decision Outcome

Chosen option: "VitePress", because it is Markdown-first - matching how the rest of the Identigon
project is already written - with a built-in "home" layout and a reasonable out-of-the-box docs
theme, without plain HTML/CSS's lack of templating past a couple of pages or Jekyll's ageing Ruby
toolchain.

### Consequences

- Good, because Markdown-first content matches how the rest of the Identigon project is already
  written, keeping one authoring format across code docs and site docs.
- Good, because the built-in "home" layout and default docs theme cover the site's actual needs (a
  landing page plus hand-written guide content) out of the box, with no bespoke templating to build
  or maintain.
- Bad, because it is a Node/npm toolchain to keep updated, on top of the monorepo's own Gradle
  toolchain, with no shared tooling between them.
- Bad, because - unlike Jekyll's zero-config native Pages support - it needs a GitHub Actions deploy
  step (`.github/workflows/deploy.yml`) as a single point of failure for publishing; accepted, since
  it is a small, standard workflow.

<!-- Migrated from identigon.github.io's own README.md "## Decisions" section (dated entry,
     2026-08-11) during the doc-kit coverage-extension migration, ADR 33. -->
