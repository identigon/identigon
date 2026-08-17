# Changelog

Release notes for the `identigon` monorepo as a whole. `alterego`, `incognito`, and `effigies`
version together (lockstep — see any subproject's "lockstep versioning" ADR): one version number,
one tag, one entry here per release, with a subsection per subproject that actually changed that
release (a subproject with nothing to report that release has no subsection).

Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) — versioned,
dated, human-curated entries — adapted for a monorepo: each version groups changes by subproject
rather than by change type.

Each subproject's own `CHANGELOG.md` (`alterego/CHANGELOG.md`, `incognito/CHANGELOG.md`,
`effigies/CHANGELOG.md`) covers everything before this file took over at 1.0.0.

## [Unreleased]

### alterego

- **Spotless/SpotBugs/PMD build config consolidated to the monorepo root.**
  `config/spotbugs/exclude.xml` moved to the shared root `config/spotbugs/exclude-alterego.xml`
  (alongside `config/pmd/ruleset.xml`); the identical Spotless/SpotBugs/PMD settings that were
  copy-pasted into each subproject's `build.gradle.kts` are now declared once in the root's
  `subprojects { }` block. Only the SpotBugs `excludeFilter` path — genuinely different per
  subproject — stays local. One user-visible build change: the SpotBugs XML report is no longer
  produced here (only HTML), matching incognito/effigies; nothing was consuming it. The
  find-sec-bugs plugin dependency (identical in all three `dependencies { }` blocks) moved into the
  same root config. JUnit BOM aligned to `6.1.3` across all three subprojects (was `5.11.4` here,
  `5.10.2` in incognito/effigies).
- **JaCoCo and Javadoc/doclint config also elevated to the root `subprojects { }` block.** JaCoCo's
  report shape (`xml`/`html`/`csv`) and `check`-task wiring moved out of this subproject's own
  `build.gradle.kts` into the shared block (`plugins.withId("jacoco")`); alterego now applies only
  `id("jacoco")` locally. Doclint's `Xdoclint:all`/`Xwerror` similarly moved into a
  `plugins.withId("maven-publish")` guard, which correctly reaches alterego/incognito (both
  publish a javadoc jar) and skips effigies (a CLI, deliberately not published) without a bespoke
  flag. No behavioural change to alterego's own reports or doclint enforcement.

### incognito

- **`DirectIdStrategy.ALTEREGO_NINO` added**, wiring the `alterego` GB National Insurance number
  generator (already present, unwired — ADR 0012) through to
  `TableTransformLoadStage` (fabrication), `AnonymisationReportBuilder` (illustrative DPIA samples),
  and `VerificationStage` (a new fictionality check: every fabricated value must carry the
  guaranteed-fictional `QQ` prefix, alongside the existing email/postcode/domain/URL checks).
  `nhsNumber()`, `passportNumber()`, `drivingLicenceNumber()`, and `creditCardNumber()` remain
  unwired — tracked in `incognito/PLAN.md`.
- **Spotless/SpotBugs config consolidated to the monorepo root** alongside the PMD move below —
  `config/spotbugs/exclude-incognito.xml`; see the alterego entry above for the mechanics, including
  the find-sec-bugs and JUnit BOM alignment.
- **`PolicyInferrer` and `AnonymisationPolicy.Builder.autoInfer(boolean)` are now
  `@Deprecated(forRemoval = true)`.** Inference is authoring, not execution — the maintained version
  has lived in `effigies`' own `PolicyInferrer` since the 1.0.0 split — and this copy is scheduled
  for removal at incognito's next major version (see
  `effigies/docs/adr/0001-authoring-above-the-engine.md`). No behavioural change yet; this is the
  deprecation notice ahead of that removal.
- **Identifier quoting fixed in both dialect handlers.** `PostgresDialectHandler` (`buildInsertSql`,
  `preLoadTable`'s owner-mode fallback, `postLoadTable`, `resyncSequence`) and
  `GenericDialectHandler.buildInsertSql` now quote every raw table/column identifier — previously
  only the FK drop/recreate path did. A reserved-word or mixed-case table/column name broke
  inconsistently depending on which code path touched it; none of the benchmark fixtures happen to
  use such names, so this was silent until now.
- **PMD added to the build**, sharing a root `config/pmd/ruleset.xml` with `alterego`/`effigies`.
  Fixed what it found: a `SchemaDiscoveryStage.validateTablePolicy` parameter that never actually
  gated anything (the fail-closed suggestion hint was always included, regardless of `autoInfer`);
  `DefaultIncognitoPipeline`'s catch split into `IncognitoException`/`Exception` branches instead of
  an `instanceof` check; the in-memory stores declare `Map` fields instead of `ConcurrentHashMap`; a
  couple of dead/redundant bits of code (an always-overwritten initializer, a no-op
  catch-and-rethrow). No behavioural change.
- **JaCoCo added to the build**, sharing the root `subprojects { }` block's report/`check`-task
  config with alterego/effigies (previously alterego-only; see the alterego entry above).

### effigies

- **Added a `examples/quickstart/` worked example** — a small first-party PostgreSQL schema
  (`customers`/`orders`/`support_tickets`, no third-party data, no Docker/Testcontainers
  dependency) with a hand-authored `policy.yaml` and a step-by-step README, so evaluating the
  `discover` → `scaffold` → `run` workflow no longer requires a real production database or one of
  incognito's Docker-gated benchmark fixtures. Linked from the main README's new "Try it in five
  minutes" section. Demonstrates every `DirectIdStrategy`/`QuasiIdStrategy` family in one schema,
  including the new `ALTEREGO_NINO` (see the incognito entry above) and,
  deliberately, the `ALTEREGO_GENERIC` fallback for a bank-account column with no typed generator
  yet.
- `PolicyInferrer` gains heuristics for postcodes (`QUASI_ID`), passport numbers, driving licence
  numbers, and credit card numbers (`DIRECT_ID`), and anchors the email/phone patterns to the end
  of the column name so a boolean like `email_verified` no longer gets suggested as DIRECT_ID.
- `ScaffoldCommand` writes its output as UTF-8 explicitly, not the platform-default charset (not
  UTF-8 on Windows) a bare `FileWriter` used.
- CLI error paths report the exception itself, not just its (often empty) message.
- Added tests for `RunCommand`, `DiscoverCommand`, `PolicyInferrer`, and `SimpleDataSource`
  (previously untested), splitting the CLI commands into a directly-testable core to do it without
  needing to fake environment variables.
- **PMD added to the build**, sharing a root `config/pmd/ruleset.xml` with `alterego`/`incognito`.
  No behavioural change here — only a `StringBuilder` under-sized for what it accumulates.
- **Spotless/SpotBugs config consolidated to the monorepo root** —
  `config/spotbugs/exclude-effigies.xml`; see the alterego entry in this section for the mechanics,
  including the find-sec-bugs and JUnit BOM alignment.
- **The runnable jar is now `identigon.jar`, not `effigies.jar`.** Consumers only ever run this
  one artifact (alterego/incognito stay internal, sibling-project dependencies), so the jar name,
  the `--version`/`--help`/usage banner text, and the manifest `Implementation-Title` now say
  "Identigon" instead of "Effigies". The Gradle module, its `org.identigon.effigies` package, and
  the `EffigiesCli` class name are unchanged — this is the public artifact name only, not a module
  rename.
- **JaCoCo added to the build**, sharing the root `subprojects { }` block's report/`check`-task
  config with alterego/incognito (previously alterego-only; see the alterego entry in that
  section). Javadoc/doclint enforcement stays deliberately absent here — effigies is a CLI, not a
  published library.

### monorepo

- **Markdown line-length lint added.** `.markdownlint-cli2.jsonc` runs `markdownlint-cli2` as a
  pre-commit hook, `MD013` only (100-column line length; code blocks, tables, and headings
  exempt) — deliberately narrow, matching what was actually asked for when the hook was added.
  Existing violations across the repo were fixed in the same change. Two more rules were checked
  read-only (full default rule set) and fixed directly since they were mechanical: every bare
  fenced code block now has a `text`/`sh` language tag (`MD040`), and every bare citation URL is
  wrapped in `<...>` (`MD034`). The rest of the default rule set (table/list formatting, heading
  conventions) needs real editorial judgment, not a mechanical fix — tracked in `PLAN.md`, not
  done here.
- **Dependabot added** (`gradle` + `github-actions`, weekly) to keep dependency versions current
  across the monorepo.
- **CI reports made downloadable.** `_build.yml` uploads each matrix leg's (`ubuntu-latest`/
  `windows-latest`) JUnit XML, JaCoCo (HTML/XML/CSV), PMD (HTML/XML), and SpotBugs (HTML) reports
  as a `build-reports-<os>` artifact via `actions/upload-artifact`, even when the build step fails
  — previously these only existed buried in the raw Gradle log.
- **Gradle version catalog added** (`gradle/libs.versions.toml`). Every shared version — root
  plugin versions, SpotBugs/PMD `toolVersion`s, find-sec-bugs, the JUnit BOM, snakeyaml, H2, the
  Testcontainers BOM, the Postgres driver — was a literal string repeated in one or more
  `build.gradle.kts` files; all ten now have exactly one declaration. No version actually changed
  (verified: the published `incognito` POM still resolves `snakeyaml` to `2.2`, matching before).
  CI's downloadable per-run artifact (see above) makes this easy to spot-check going forward: the
  JaCoCo/PMD/SpotBugs reports inside it reflect whatever the catalog resolved.

## [1.0.0] — 2026-08-10

### alterego

- Merged into the `identigon` monorepo alongside `incognito` and `effigies`, each a Gradle
  subproject with full history preserved. Versioning is now lockstep across all three, sourced from
  the monorepo root (see `alterego/docs/adr/0015-lockstep-versioning.md`). A deliberate re-baseline
  to 1.0.0, not a claim that four minor versions' worth of API changes happened at once — alterego's
  own history before this point is in `alterego/CHANGELOG.md`.

### incognito

- Merged into the `identigon` monorepo alongside `alterego` and `effigies`, each a Gradle
  subproject with full history preserved. Versioning is now lockstep across all three, sourced from
  the monorepo root (see `incognito/docs/adr/0008-lockstep-versioning.md`). Moves backward in number
  from incognito's last independent release, `1.1.0` → `1.0.0` — deliberate, not a downgrade; see
  the ADR.
- **`DirectIdStrategy`: `ALTEREGO_POSTCODE`, `ALTEREGO_DOMAIN`, `ALTEREGO_URL`.** Three previously
  unexposed `alterego` typed generators (`postcode()`, `domainName()`, `url()`) are now reachable
  from policy. `VerificationStage` positively asserts each strategy's fictionality guarantee on the
  target (GB postcode inward-code letter; RFC 2606 reserved domain/TLD for domain/URL), and these
  strategies are excluded from the generic DIRECT_ID survival check, same as `ALTEREGO_EMAIL`.

### effigies

- Merged into the `identigon` monorepo alongside `alterego` and `incognito`, each a Gradle
  subproject with full history preserved. Versioning is now lockstep across all three, sourced from
  the monorepo root (see `effigies/docs/adr/0002-lockstep-versioning.md`). The version number itself
  is unchanged from effigies' prior standalone release — both are 1.0.0 — this entry exists so that
  change in what "1.0.0" means isn't silently missing from the record.
