---
status: "accepted"
date: 2026-09-03
decision-makers: David Conneely
---

# 36. Adopt Prettier for Markdown, alongside `markdownlint-cli2`, accepting its emphasis-style rewrite

## Context and Problem Statement

`markdownlint-cli2`'s `MD013` (line length, 100 columns) has no auto-fix in any implementation -
checked directly against this hook, the npm `markdownlint-cli` package, and the Ruby `mdl` gem.
Every rewrap today is manual: edit, lint, find the violation, count columns, rewrap by hand,
re-lint. `identigon.github.io` already runs Prettier scoped to `*.md` for exactly this, via an
unpinned `npx prettier --write` pre-commit hook.

The obstacle specific to _this_ repo: Prettier's markdown printer has no configuration option for
single-emphasis style - confirmed empirically (a bare `npx prettier` run, no config file in scope,
against `*text*` still rewrites it to `_text_`). This repo's actual existing convention is
asterisks: a repo-wide scan found roughly 113 clean, unambiguous instances of `*word*` used as
italic emphasis in prose, and zero genuine uses of `_word_` for the same purpose (an initial broader
match on underscores turned out to be entirely snake_case code identifiers inside backticks -
`` `DIRECT_ID` ``, `` `created_at` `` - not markdown emphasis syntax at all). Adopting Prettier here
is therefore not "run it and review the diff" the way it was for the site repo; it is "accept a
permanent, project-wide switch from `*word*` to `_word_` for italics, with no way to configure
around it, in exchange for automated line-wrapping."

## Considered Options

- Adopt Prettier, accept the emphasis-style switch as the cost of automated wrapping.
- Don't adopt Prettier; keep the manual rewrap-by-hand cost `MD013` already imposes today.
- Look for a narrower tool that fixes line-wrapping only, without Prettier's opinionated full
  reformat (e.g. a `remark`-based line-wrap-only plugin) - investigated only enough to note no such
  tool was found readily available; not pursued further once the second option below was chosen over
  it anyway.

## Decision Outcome

Chosen option: "adopt Prettier, accept the emphasis-style switch", because the automated-wrapping
benefit was judged worth a one-time, mechanical, repo-wide style change that costs nothing to keep
consistent going forward (Prettier itself enforces it on every subsequent edit). Prettier
complements `markdownlint-cli2` rather than replacing it - a formatter (wrapping, table alignment,
list-marker consistency) and a linter (structural rules Prettier doesn't check at all: `MD024`
duplicate headings, `MD032` list spacing, `MD060` table style, and the rest of the enabled default
rule set) are different jobs, and both stay.

Wired as a `local` pre-commit hook (`npx --yes prettier@3.9.6 --write`), not the
`pre-commit/mirrors-prettier` repo: that mirror is stale (its newest stable tag is `v3.1.0`, several
minor versions behind Prettier's own release line), so this pins the exact version directly via
`npx`, the same pattern already used for every other `npx`-invoked tool in this repo's own tooling
(`markdownlint-cli2` included, though that one _also_ has a maintained pre-commit mirror to pin via
`rev:` - Prettier does not, which is exactly why this repo's own version-pinning discipline has to
be applied by hand here instead of delegated to a mirror). Scoped to `*.md` only, matching
`identigon.github.io`'s own scoping - this repo's Java/Kotlin/YAML files are untouched by Spotless's
own remit already.

### Consequences

- Good, because future edits no longer need manual line-counting and rewrapping - `prettier --write`
  does it, the same way `spotlessApply` already does for Java formatting.
- Good, because the two tools' responsibilities don't overlap: Prettier never needs to know
  `markdownlint-cli2`'s structural rules, and `markdownlint-cli2` needs no line-wrap logic of its
  own (it has none, and never will per its own upstream stance).
- Bad, because every existing `*word*` in the repo's ~59 tracked Markdown files becomes `_word_` in
  one bulk reformat commit - a large diff with no semantic content, reviewed as such rather than
  read line by line.
- Bad, because Prettier's own version now has to be bumped by hand in `.pre-commit-config.yaml` when
  it drifts, rather than via the shared `gradle/libs.versions.toml` catalog (which only
  Gradle-resolved dependencies go through) or a pinned mirror `rev:` (unavailable here, per above).
- Neutral: table cells also get alignment padding (`| a | b |` -> `| a   | b   |`) as a side effect
  of the same bulk reformat - cosmetic, and not a decision point of its own.
- Good, because the same `embeddedLanguageFormatting: "off"` fix was mirrored into
  `identigon.github.io`'s own `.prettierrc.json` once found here - that repo's Prettier hook
  (pre-existing, unrelated to this decision) had the same unset default and the same class of YAML
  content at risk, even though its current examples happen not to trigger the corruption.
