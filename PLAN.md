# Identigon — Monorepo Infrastructure Plan

Tracks backlog that spans more than one subproject's own build — the things a single subproject's
own `PLAN.md` (scoped to that subproject's own `SPECIFICATION.md`-defined behaviour) isn't the right
place for. See the root `CHANGELOG.md` for what's already shipped in this category.

## Outstanding

- [ ] **Gradle version catalog (`gradle/libs.versions.toml`).** Every shared version — root plugin
  versions, SpotBugs/PMD `toolVersion`s, find-sec-bugs, the JUnit BOM, H2, the Testcontainers BOM,
  the Postgres driver — is currently a literal string in one or more `build.gradle.kts` files. A
  catalog would be the idiomatic Gradle-native single source of truth for all of it at once. Bigger
  and more structural than the `subprojects { }` consolidation already done (moving config into a
  shared block vs. introducing a new file format/convention), so tracked here separately rather than
  folded into that work.
- [ ] **Consider turning on more of `markdownlint-cli2`'s rule set, not just line length.**
  `.markdownlint-cli2.jsonc` currently enables only `MD013` (`default: false`) — deliberately
  narrow, matching what was actually asked for when the hook was added. Checked read-only with
  the full default rule set enabled (`default: true`, same MD013 overrides): 279 violations.
  `MD040` fenced-code-language (22) and `MD034` no-bare-urls (21) were straightforward — every
  bare fence got a `text`/`sh` language tag, every bare citation URL wrapped in `<...>` — fixed
  directly, not tracked here. What's left is real editorial judgment, not a mechanical fix:
  `MD060` table-column-style (140 — every table would need padding/alignment reformatting),
  `MD007` ul-indent (56), plus ~40 more across
  `MD022`/`MD031`/`MD032`/`MD024`/`MD041`/`MD036`/`MD012`/`MD033` (blank-line, heading, and
  duplicate-heading conventions). Picking which (if any) of these to enable is a separate
  decision, not bundled here.
  Also worth setting `MD013.strict: true` at the same time as any widening: the default
  (non-strict) mode is more lenient than "100 columns, no exceptions" — it forgives a trailing
  word that starts within the limit but ends past it, so a line can run slightly over 100 and
  still pass (found by testing, not by reading the docs). `strict` removes that slack but also
  removes the automatic long-URL/link-only-line exemption, so switching would need the 8
  already-identified long-URL lines individually suppressed (`<!-- markdownlint-disable-line
  MD013 -->`) rather than passing implicitly.
