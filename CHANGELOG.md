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

### incognito

- **Identifier quoting fixed in both dialect handlers.** `PostgresDialectHandler` (`buildInsertSql`,
  `preLoadTable`'s owner-mode fallback, `postLoadTable`, `resyncSequence`) and
  `GenericDialectHandler.buildInsertSql` now quote every raw table/column identifier — previously
  only the FK drop/recreate path did. A reserved-word or mixed-case table/column name broke
  inconsistently depending on which code path touched it; none of the benchmark fixtures happen to
  use such names, so this was silent until now.

### effigies

- `PolicyInferrer` gains heuristics for postcodes (`QUASI_ID`), passport numbers, driving licence
  numbers, and credit card numbers (`DIRECT_ID`), and anchors the email/phone patterns to the end
  of the column name so a boolean like `email_verified` no longer gets suggested as DIRECT_ID.
- `ScaffoldCommand` writes its output as UTF-8 explicitly, not the platform-default charset (not
  UTF-8 on Windows) a bare `FileWriter` used.
- CLI error paths report the exception itself, not just its (often empty) message.
- Added tests for `RunCommand`, `DiscoverCommand`, `PolicyInferrer`, and `SimpleDataSource`
  (previously untested), splitting the CLI commands into a directly-testable core to do it without
  needing to fake environment variables.

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
