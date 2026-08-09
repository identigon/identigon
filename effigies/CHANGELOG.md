# Changelog

All notable changes to Effigies are recorded here. Format loosely follows [Keep a
Changelog](https://keepachangelog.com/en/1.1.0/).

## Invariants (hold across every release within a major version)

- **Metadata only.** Discovery and every emitted artifact carry schema metadata, never sampled real
  data values (SPECIFICATION.md §2). A change here is a breaking change.
- **Fail-closed preserved.** Effigies suggests roles; it never assigns one, and never emits a
  runnable policy that silently defaults a column. An unclassified column still aborts the
  lib-incognito run.
- **No model in the engine path.** Inference is authoring; the anonymisation run is a deterministic,
  model-free lib-incognito execution driven by the reviewed `policy.yaml`.
- **Secrets stay out of the config.** Credentials and fixed/`persistent` salt bytes are injected
  out-of-band, never written into the policy or any committed file.

## [Unreleased]

### Added

- `discover` subcommand: Inspects a source database using `lib-incognito`'s `SchemaInspector` and prints a metadata-only summary.
- `scaffold` subcommand: Emits a fail-closed starter `policy.yaml` with schema metadata as comments.
- Project skeleton: Gradle (Kotlin DSL) build with a Java 25 toolchain and a single runnable jar
  (`java -jar`); Spotless + SpotBugs/find-sec-bugs; pre-commit hooks + gitleaks; a CI workflow that
  builds lib-alterego and lib-incognito to Maven local first.
- `EffigiesCli` dispatch skeleton (`discover` / `scaffold` / `run` declared; `help` / `version`
  live), covered by `EffigiesCliTest`.
- Base docs: `README.md`, `SPECIFICATION.md`, `PLAN.md`, `docs/adr/` (ADR 0001 — authoring above the
  engine), and an initial `docs/tasks/` handoff for schema discovery + scaffold.
