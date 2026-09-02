---
status: "accepted"
date: 2026-09-01
decision-makers: David Conneely
---

# 30. Standalone jar assembly lives in effigies, not the root project

## Context and Problem Statement

ADR 28 assembled the standalone-runnable `identigon.jar` at the monorepo root, in a new
`identigonJar` task, reasoning that only the root project could legitimately speak for the whole
product. That bundled two separable questions together: which Maven _coordinate_ the fat jar
publishes under, and which Gradle project's build script owns the _task_ that assembles it.

The coordinate question has a real technical answer, unchanged by this record: publishing the fat
jar under its own plain GAV would let a dependency resolver pick it up like a normal library,
duplicating incognito/alterego/SnakeYAML/the Postgres driver (embedded in the jar, and again
transitively from the POM). It publishes as a `standalone`-classified artifact under
`org.identigon:effigies` instead - a classifier is never selected unless asked for by name.

The task-ownership question does not have the same weight. Root-owning the assembly cost real
machinery - the `java-base` plugin applied just to enable attribute-matched resolution, a
`repositories {}` block, a detached configuration resolving `project(":effigies")`'s runtime
classpath from outside that project - to buy a naming argument (root is the only scope that can
"speak for" the whole product) that does not actually depend on which build script declares the
task: a task in `effigies/build.gradle.kts` can name its output `identigon.jar` exactly as well.
effigies' own `publishing {}` block also needs a reference to whichever task builds the fat jar
either way, since that is where it is published from - the cross-project reference does not
disappear, it just changes direction.

## Considered Options

- Keep assembly at the root project (ADR 28's original shape).
- Move assembly into `effigies/build.gradle.kts`, as a task separate from `jar` (so a normal thin
  jar - the real Maven artifact's primary output - is unaffected), publishing exactly as before.

## Decision Outcome

Chosen option: "move assembly into effigies", because the root-ownership argument survives on
naming alone once the coordinate question is settled separately, and a task local to the project
whose runtime classpath it packages is simpler to write and consume than one resolving that
classpath from outside via a detached configuration.

- **`identigonJar` moves to `effigies/build.gradle.kts`.** It consumes effigies' own
  `sourceSets.main.output` and `configurations.runtimeClasspath` directly - no attribute-matching
  configuration, no `project(":effigies")` dependency declaration, no root `repositories {}` block.
  `effigies/build/libs/identigon.jar` is where it lands now, not `build/libs/identigon.jar`.
- **The root project goes back to being a pure aggregator.** No JVM plugin, no buildable task, no
  artifact of its own - `docs/adr/0024-lockstep-versioning.md`'s framing of "three Gradle modules"
  stays literally true.
- **The published coordinate is unchanged**: `org.identigon:effigies`, fat jar as a `standalone`
  classifier, thin jar as the primary artifact. Nothing about ADR 28's publishing shape, the GitHub
  Release asset copy/rename step, or the attestation step changes - only the path
  `.github/workflows/release.yml` copies the fat jar from.

### Consequences

- Good, because root's build script has no buildable-artifact machinery to maintain, and the fat
  jar's assembly sits next to the thin jar it is a variant of - the more idiomatic Gradle shape,
  and where a reader would look for it first.
- Good, because `identigonJar` consuming effigies' own `runtimeClasspath` configuration directly is
  fewer moving parts than resolving the same classpath from outside via a detached configuration
  with hand-declared `Usage`/`Category`/`LibraryElements`/`Bundling` attributes.
- Neutral: `java -jar identigon.jar` now means `effigies/build/libs/identigon.jar`, not
  `build/libs/identigon.jar` - a path change for anyone running it locally from a source checkout,
  caught before this shipped in a release.
- Bad, because this is the second placement decision for the same task inside one release cycle.
  Caught and corrected before v2.0.0 shipped, so no published artifact or documented path ever
  pointed at the root location.
