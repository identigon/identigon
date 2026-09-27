# Task: present Identigon as one command-line tool

Status: not started. Delete once implemented and green.

## 1. The gap

User feedback on the quickstart: the steps are clear and easy to follow, but people who want to
de-identify a database don't care that the tool is built from `alterego`, `incognito` and
`effigies`. They want to run `identigon` without learning how it works inside. Today the component
names are the first thing a newcomer meets, and they keep turning up along the whole CLI path:
README, quickstart, `help`, `version`, scaffold comments, error messages and the agent skill.

The component names stay correct for **deeper use**: anyone embedding a library, contributors, and
the specs and ADRs. The goal is that an ordinary CLI user can go from download to a finished clone
without meeting them, not that the names are removed from the repository.

## 2. Where component names reach a CLI user (as of this note)

Docs:

- `README.md` - the first screen is the three-subproject table and the dependency chain; the
  quickstart pointer comes after it. A CLI user needs: what it does, how to get it, try it in five
  minutes. The subproject table belongs further down, under something like "Using the libraries
  directly".
- `quickstart/README.md` - "Point `effigies` at it", and every command runs
  `../effigies/build/libs/identigon.jar`, so a user has to build the repository and learn a
  subproject's directory layout. Better: a released `identigon.jar` (or the executable from the
  self-contained-executable entry in `PLAN.md`), with building from source as the fallback.
- `.agents/skills/identigon-policy-author/SKILL.md` - describes itself as authoring
  "incognito/effigies anonymisation policies" and tells the agent to run `effigies validate` /
  `effigies run`. No `effigies` command exists: the user runs `java -jar identigon.jar validate`.
- The site (`identigon.github.io`) - check Getting Started the same way. The existing
  "Per-subproject pages on the site" entry in `PLAN.md` should put those pages under a
  library/contributor section, not on the getting-started path.

CLI output (`effigies/src/main/java/org/identigon/effigies/`):

- `EffigiesCli.printUsage` - "author and run an incognito anonymisation"; `run` "Execute incognito
  against a finished policy.yaml".
- `EffigiesCli.run` (`version`) - prints `(engine: incognito on classpath)`.
- `ScaffoldCommand.writeRoleStub` - points the user at `docs/spec/incognito.md §4.1` and at
  "DirectIdStrategy's Javadoc": a repository path and Java API docs, handed to someone editing YAML.
  The self-explaining-scaffold task covers this in detail.

Error messages (`incognito/src/main/java/org/identigon/incognito/core/`):

- `ConfigException` messages cite `SPEC §4.1`, `SPEC Appendix B`, `ADR 31`, `SPEC §9` and the Java
  enum type names (`QuasiIdStrategy`, "a directIdStrategy hint"). This is about 25 strings, most of
  them in `SchemaDiscoveryStage` (6) and `TableTransformLoadStage` (10), and they reach the CLI user
  verbatim through `CliErrors.causeChain`. Each should say what is wrong with which table/column and
  what to write in `policy.yaml` to fix it, using the policy's own key names. A spec section may
  follow as a pointer for library users, but it must not be the fix itself. The same wording serves
  library users just as well, so this is a wording change, not a translation layer in `effigies`.
- DPIA report (`DpiaArtefactEmitter`, `AnonymisationReportBuilder`) - check which of its mentions of
  `incognito`/`alterego` end up in the rendered HTML/Markdown a reviewer reads, and rephrase them in
  terms of the tool.

## 3. Not in scope

- Renaming the Gradle modules, Java packages or Maven coordinates - library users need them, and CLI
  users never see them once the points above are fixed.
- Removing component names from `docs/spec/`, `docs/adr/`, `CHANGELOG.md` or Javadoc.
- Policy key and value names (`directIdStrategy`, `ALTEREGO_NAME`) - see
  `incognito-plainer-policy-vocabulary.md`.

## 4. Done when

A newcomer following the root README into the quickstart, then scaffolding, validating (including a
deliberately broken policy) and running, meets no subproject name, repository path, spec section
number or Java type name, and still knows at every step what to do next.
