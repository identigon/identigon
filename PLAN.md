# Identigon — Monorepo Infrastructure Plan

Tracks backlog that spans more than one subproject's own build — the things a single subproject's
own `PLAN.md` (scoped to that subproject's own `SPECIFICATION.md`-defined behaviour) isn't the right
place for. See the root `CHANGELOG.md` for what's already shipped in this category.

## Outstanding

- [ ] **JaCoCo made consistent across subprojects.** Only `alterego` has it today; `incognito`'s and
  `effigies`' own `PLAN.md`s each separately list it as "optional / consistency-only" backlog.
  Worth deciding once, for all three, rather than as three independent calls.
- [ ] **Javadoc/doclint configuration made consistent.** `alterego` relies on javadoc's own default
  doclint level (already full-strict) plus `Xwerror`; `incognito` states `Xdoclint:all` explicitly
  before `Xwerror`. Not a behavioural difference today — javadoc's default already *is*
  `-Xdoclint:all` — just an explicitness/documentation inconsistency between the two build scripts,
  worth settling on one canonical form. `effigies` has no Javadoc enforcement at all, correctly — it's
  a CLI, not a published library (no `withJavadocJar()`).
- [ ] **Gradle version catalog (`gradle/libs.versions.toml`).** Every shared version — root plugin
  versions, SpotBugs/PMD `toolVersion`s, find-sec-bugs, the JUnit BOM, H2, the Testcontainers BOM,
  the Postgres driver — is currently a literal string in one or more `build.gradle.kts` files. A
  catalog would be the idiomatic Gradle-native single source of truth for all of it at once. Bigger
  and more structural than the `subprojects { }` consolidation already done (moving config into a
  shared block vs. introducing a new file format/convention), so tracked here separately rather than
  folded into that work.
