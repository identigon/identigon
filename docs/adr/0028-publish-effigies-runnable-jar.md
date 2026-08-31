---
status: "accepted"
date: 2026-08-31
decision-makers: David Conneely
---

# 28. Publish the effigies runnable jar somewhere a user can download it

## Context and Problem Statement

`effigies/build.gradle.kts` already builds `identigon.jar` - a runnable fat jar (incognito,
alterego, SnakeYAML, the Postgres driver, and a merged, correctly-attributed `META-INF/LICENCE`,
all bundled) via plain `./gradlew build`. ADR 24 already settled that effigies "ships as a runnable
jar, not a library" - it deliberately has no `publishing {}` block and isn't a Maven artifact like
`alterego`/`incognito`. But nothing currently uploads that jar anywhere durable: `main.yml`'s
`publish` job runs `./gradlew publish`, which is a no-op for effigies (nothing to publish), and
releases themselves are cut manually (a `build: Release vX.Y.Z` commit bumping the version, tagged
and pushed by hand - there's no tag-triggered workflow at all today). A user who wants effigies
today must clone and build it.

`alterego`/`incognito` being on GitHub Packages does not close this gap: GPR requires an
authenticated, `read:packages`-scoped token to download *even a public package* - real friction for
someone trying to `curl` a CLI tool and run it, and a different audience from a Java library
consumer who already has Maven/Gradle credentials configured. This decision is specifically about
the effigies CLI jar, not about whether alterego/incognito are published (they already are).

## Considered Options

* GitHub Release asset only - `gh release upload` attaches a jar to the tagged release. Anonymous,
  unauthenticated HTTPS download; the natural fit for "download and run" quickstart use, but a
  one-off mechanism separate from how alterego/incognito are already distributed.
* GitHub Packages only (Maven format) - publish effigies as a `publishing {}` artifact the same way
  alterego/incognito already are. GPR has no generic/arbitrary-file package type - confirmed against
  GitHub's own docs, only npm, Maven, NuGet, RubyGems and Docker/OCI are supported - so this route
  means giving effigies Maven coordinates for a jar that isn't meant to be a dependency. Consistent
  single mechanism, but inherits GPR's auth requirement (a `read:packages`-scoped token) even for a
  public download.
* Both.
* Neither - leave clone-and-build as the only path (status quo).

## Decision Outcome

Chosen option: "Both", because GitHub Packages gives every artifact a permanent, versioned home
using the mechanism alterego/incognito already use, while a GitHub Release asset solves the specific
"curl it and run it, no token" friction that prompted this record - and reusing one build's output
for both means there is exactly one artifact per version to reason about, not two.

Specifically:

* **effigies' own `jar` task reverts to producing a normal thin jar, and the fat-jar assembly moves
  to the root `build.gradle.kts`.** Today's `effigies/build.gradle.kts` overrides the module's
  default `jar` task itself to *be* the fat merge - there is no artifact anywhere containing only
  effigies' own classes. Publishing that fat output under a plain Maven coordinate would be wrong: a
  consumer resolving `org.identigon:effigies` transitively would get incognito/alterego/SnakeYAML/
  the Postgres driver *twice* - once already embedded in the jar, once again from the POM's own
  resolved dependencies (split-package / duplicate-class classpath problems). So the ordinary `jar`
  task goes back to producing effigies' own classes only, unmodified. The fat merge itself belongs
  at the root, not inside effigies: `identigon.jar` is named for `rootProject.name`
  (`settings.gradle.kts`: `"identigon"`), not for the effigies subproject - it represents the whole
  monorepo, which only the root project can legitimately speak for, and effigies' own build file
  shouldn't be the one place conflating "effigies-the-component" (now a real,
  independently-published Maven artifact) with "identigon-the-product". A new root-level task
  (`identigonJar`, not `jar` - naming it plain `jar` would collide with `./gradlew jar` also running
  every subproject's own `jar` task) resolves a detached configuration depending on
  `project(":effigies")` (which already transitively pulls in
  incognito and alterego - root doesn't need to name either directly) and does exactly what today's
  `tasks.jar` block does: `archiveFileName = "identigon.jar"`, the same manifest, the same
  LICENCE/NOTICE handling, merged for the standalone-runnable download. This is root's first
  buildable artifact - today it has no `java`/`application` plugin and produces nothing of its own,
  only a `subprojects { }` block that injects config *into* subprojects - but root is already this
  repo's established home for monorepo-wide facts (`PLAN.md`, `CHANGELOG.md`, `docs/adr/`), and a
  monorepo-wide artifact is the same shape of fact, not a new pattern being invented for the
  occasion.
* **effigies gains a minimal `publishing {}` block for the thin jar** - a jar + POM under
  `org.identigon:effigies`, no javadoc/sources jars (ADR 24's rationale for skipping those doesn't
  apply to a bare-jar publish), with a normal, accurate POM (incognito/SnakeYAML/the Postgres driver
  declared as real dependencies) - the same shape alterego/incognito already publish. If the fat
  `identigon.jar` is *also* wanted on GitHub Packages, it goes under that same
  `org.identigon:effigies` coordinate with a classifier (e.g. `standalone`), not as the primary
  artifact - the standard way to offer a shaded jar alongside a real one without a dependency
  resolver ever picking it up by accident.
* **The same CI run that already builds all the jars also copies and renames them** -
  `alterego.jar`, `incognito.jar`, `identigon.jar` (no version in the filename; each release's own
  asset list is already scoped by its tag) - and attaches them to the GitHub Release via
  `gh release upload`. This deliberately does not re-fetch from GitHub Packages after publishing; it
  reuses the local build output already sitting in each subproject's `build/libs/`, so the two
  destinations are guaranteed byte-identical from one build, not two independent ones. The thin
  `effigies.jar` isn't part of this set by default - it can't run standalone, which is the point of
  a Release asset - but nothing rules it out later if a use for it as a download surfaces.
* **`identigon.jar` stays the existing fat jar**, unchanged in content and filename despite moving
  build files (all runtime deps bundled, standalone-runnable via `java -jar`); **`alterego.jar` and
  `incognito.jar` stay their plain, non-fat library jars**, unchanged from what's already on GitHub
  Packages today - bundling their transitive deps would make them unsafe to depend on via
  Maven/Gradle, and that isn't what they're for.
* **Integrity: `actions/attest` against all three renamed jars** (a `subject-path` glob), producing
  a keyless, OIDC-signed, SLSA-shaped provenance attestation per artifact - independent of which
  channel it later reaches, so it covers both. Not `actions/attest-build-provenance`: as of that
  action's own v4, it is "simply a wrapper on top of `actions/attest`", and its README now directs
  new implementations to use `actions/attest` directly - no reason to build on the wrapper.
  `gh attestation verify <file> -R identigon/identigon` verifies any of them. This is the primary
  integrity mechanism; a plain `SHA256SUMS` file alongside the Release assets is a cheap,
  `gh`-CLI-free complement worth adding too.
* **Scope: the fat `identigon.jar` plus the two plain library jars, nothing else.** No native image,
  no platform installer - if that need surfaces later it's a separate, independently-justified
  `PLAN.md` item.
* **Trigger: a new, manually-dispatched release workflow that creates the tag itself.**
  `.github/workflows/release.yml` (`on: workflow_dispatch`) - run by hand once a commit bumping the
  version and its `CHANGELOG.md` entry is already pushed to `main`; no tag needs to exist yet. The
  workflow reads `baseVersion` out of root `build.gradle.kts`, creates the `vX.Y.Z` tag on the
  checked-out commit and pushes it itself, then builds, publishes the now-exactly-tagged release
  version to GitHub Packages, renames and uploads the jars to a GitHub Release it creates for that
  tag, and runs `actions/attest`. This replaces today's fully-manual `gh release create` ritual with
  one dispatched job rather than several hand-run commands - a step up without going as far as
  triggering automatically on every push to `main`. `main.yml`'s existing `publish` job is untouched
  and keeps publishing SNAPSHOT versions on every ordinary push, as it does today. Having the
  workflow create the tag itself - rather than a human pushing it beforehand - also sidesteps a
  publish-ordering hazard the alternative would have had (see Consequences): the tag cannot exist
  before this run, so whichever `main.yml` run already fired for that commit necessarily saw no tag
  and published a SNAPSHOT, never the release version.
* **Support policy.** Deliberately left unstated for now - not decided either way, not implied by
  this record.
* **Docs follow-up.** Once the assets actually exist, `quickstart/README.md` (this repo) and
  `getting-started.md` (`identigon.github.io`, already tracked in its own Roadmap) should link them.

### Consequences

* Good, because a user gets a working tool with one unauthenticated download, and the library jars
  gain a second, token-free distribution path too - all from artifacts CI already builds, at the
  cost of one rename-and-upload step and one attestation step.
* Good, because a single build feeding both destinations means there is exactly one artifact per
  version to reason about, not two that could silently drift apart.
* Bad, because it nuances ADR 24's "effigies... has no `publishing {}` block" consequence note -
  effigies gains one now, though still not as a dependency-consumable library; the boundary ADR 24
  actually decided (effigies isn't a Maven *dependency* target) is unchanged, this just gives its
  jar a GitHub Packages home too.
* Neutral: the new `release.yml` workflow needs its own permissions - `attestations: write` and
  `id-token: write` for the attestation step, `contents: write` for both the tag push and the
  Release-asset upload, `packages: write` for the publish step - none of which `main.yml` needs to
  gain, since it stays unchanged.
* Good, because `release.yml` creating the tag itself removes what would otherwise be a real
  publish-ordering hazard: if a human pushed the tag before dispatching the workflow, and that push
  happened to land before `main.yml`'s own run against the version-bump commit had checked out (or
  that run simply hadn't finished yet), `main.yml`'s `publish` job would resolve the exact release
  version via `git describe --tags --exact-match` and publish it there first - leaving
  `release.yml`'s later publish step rejected, since a Maven release coordinate is normally
  immutable once published. Because the workflow controls exactly when the tag comes into existence,
  this can no longer happen.
* Neutral: root `build.gradle.kts` gains its first buildable task and its first dependency
  declaration on a specific subproject (`project(":effigies")`, to resolve the fat jar's classpath).
  Considered and rejected: a fourth, dedicated "packaging" subproject would have kept root a pure
  aggregator (some multi-module Gradle conventions prefer that), but is disproportionate ceremony
  here - a new `settings.gradle.kts` include, a `DOC-MAP.md`/README update to the "three Gradle
  modules" framing - for what the root-task approach does in about the same number of lines it
  already occupied inside `effigies/build.gradle.kts`.
