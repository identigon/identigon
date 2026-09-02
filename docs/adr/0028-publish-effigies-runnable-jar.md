---
status: "accepted (refined by ADR-0030)"
date: 2026-09-01
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
authenticated, `read:packages`-scoped token to download _even a public package_ - real friction for
someone trying to `curl` a CLI tool and run it, and a different audience from a Java library
consumer who already has Maven/Gradle credentials configured. This decision is specifically about
the effigies CLI jar, not about whether alterego/incognito are published (they already are).

## Considered Options

- GitHub Release asset only - `gh release upload` attaches a jar to the tagged release. Anonymous,
  unauthenticated HTTPS download; the natural fit for "download and run" quickstart use, but a
  one-off mechanism separate from how alterego/incognito are already distributed.
- GitHub Packages only (Maven format) - publish effigies as a `publishing {}` artifact the same way
  alterego/incognito already are. GPR has no generic/arbitrary-file package type - confirmed against
  GitHub's own docs, only npm, Maven, NuGet, RubyGems and Docker/OCI are supported - so this route
  means giving effigies Maven coordinates for a jar that isn't meant to be a dependency. Consistent
  single mechanism, but inherits GPR's auth requirement (a `read:packages`-scoped token) even for a
  public download.
- Both.
- Neither - leave clone-and-build as the only path (status quo).

## Decision Outcome

Chosen option: "Both", because GitHub Packages gives every artifact a permanent, versioned home
using the mechanism alterego/incognito already use, while a GitHub Release asset solves the
"curl it and run it, no token" friction that prompted this record - and reusing one build's output
for both means there is exactly one artifact per version to reason about, not two.

- **effigies' own `jar` task produces a normal thin jar; the fat jar is assembled at the root.**
  Publishing a fat jar under a plain Maven coordinate would be wrong: a consumer resolving
  `org.identigon:effigies` transitively would get incognito/alterego/SnakeYAML/the Postgres driver
  _twice_ - once embedded in the jar, once again from the POM's own resolved dependencies. So
  effigies' `jar` task produces only its own classes. The fat merge lives in the root
  `build.gradle.kts` instead, as a new `identigonJar` task: `identigon.jar` is named for
  `rootProject.name`, not for the effigies subproject, since it bundles incognito's and alterego's
  classes too - the root is the only scope that can legitimately speak for the whole product, and
  effigies' own build file shouldn't be the one place conflating "effigies-the-component" (now a
  real, independently-published Maven artifact) with "identigon-the-product". The root task
  resolves a detached configuration depending on `project(":effigies")` (incognito and alterego
  arrive transitively, so root never names either directly) and does what effigies' `tasks.jar`
  block did before: `archiveFileName = "identigon.jar"`, the same manifest, the same LICENCE/NOTICE
  handling. This is root's first buildable artifact - it otherwise applies no `java`/`application`
  plugin and produces nothing of its own, only a `subprojects { }` block that injects config _into_
  subprojects - but root is already this repo's established home for monorepo-wide facts
  (`PLAN.md`, `CHANGELOG.md`, `docs/adr/`), and a monorepo-wide artifact is the same shape of fact.
- **effigies gains a `publishing {}` block for the thin jar** - a jar + POM under
  `org.identigon:effigies`, no javadoc/sources jars (ADR 24's rationale for skipping those doesn't
  apply to a bare-jar publish), with an accurate POM (incognito/SnakeYAML/the Postgres driver
  declared as real dependencies) - the same shape alterego/incognito already publish. The fat
  `identigon.jar` is published there too, under that same coordinate with a `standalone`
  classifier, not as the primary artifact - the standard way to offer a shaded jar alongside a real
  one without a dependency resolver ever picking it up by accident.
- **The same CI run that builds all the jars also copies and renames them** - `alterego.jar`,
  `incognito.jar`, `identigon.jar` (no version in the filename; each release's own asset list is
  already scoped by its tag) - and attaches them to the GitHub Release. This does not re-fetch from
  GitHub Packages after publishing; it reuses the local build output already sitting in each
  subproject's `build/libs/`, so the two destinations are byte-identical from one build, not two
  independent ones. The thin `effigies.jar` isn't part of this set - it can't run standalone, which
  is the point of a Release asset.
- **`identigon.jar` stays the fat jar** (all runtime deps bundled, standalone-runnable via
  `java -jar`); **`alterego.jar` and `incognito.jar` stay their plain, non-fat library jars** -
  bundling their transitive deps would make them unsafe to depend on via Maven/Gradle, and that
  isn't what they're for.
- **Integrity: `actions/attest` against the three renamed jars** (a `subject-path` glob), producing
  a keyless, OIDC-signed, SLSA-shaped provenance attestation per artifact, independent of which
  channel it later reaches. Not `actions/attest-build-provenance`: as of that action's own v4 it is
  a wrapper over `actions/attest`, and its README points new implementations at the latter
  directly. `gh attestation verify <file> -R identigon/identigon` verifies any of them. A plain
  `SHA256SUMS` file alongside the Release assets is a `gh`-CLI-free complement.
- **Scope: the fat `identigon.jar` plus the two plain library jars, nothing else.** No native
  image, no platform installer - a separate, independently-justified `PLAN.md` item if that need
  surfaces.
- **Trigger: a manually-dispatched release workflow that takes an existing tag as input, rather
  than creating one.** `.github/workflows/release.yml` (`workflow_dispatch`, a required `tag`
  input) runs once a release tag already exists and is pushed. Tag creation stays manual and local:
  every release tag is SSH-signed with the maintainer's own key, which CI cannot and should not
  hold, so the workflow checks out the tag it's given rather than creating one. The release ritual
  is: bump `baseVersion` and the matching `CHANGELOG.md` section, push that commit to `main` and
  let the ordinary build finish against it (publishing it as a SNAPSHOT), then `git tag -s vX.Y.Z
&& git push origin vX.Y.Z`, then dispatch this workflow with that tag name. It cross-checks the
  version the tag encodes against `baseVersion` at that commit (catching a stale tag or a forgotten
  version bump), builds, publishes the release version to GitHub Packages, renames and uploads the
  jars to a GitHub Release it creates for the tag, and runs `actions/attest`. `main.yml`'s existing
  `publish` job is untouched and keeps publishing SNAPSHOT versions on every ordinary push.
- **Support policy.** Deliberately left unstated - not decided either way, not implied by this
  record.
- **Docs follow-up.** `quickstart/README.md` (this repo) and `getting-started.md`
  (`identigon.github.io`, tracked in its own Roadmap) should link the assets once they exist.

### Consequences

- Good, because a user gets a working tool with one unauthenticated download, and the library jars
  gain a second, token-free distribution path too - all from artifacts CI already builds.
- Good, because a single build feeding both destinations means there is exactly one artifact per
  version to reason about, not two that could silently drift apart.
- Bad, because it nuances ADR 24's "effigies... has no `publishing {}` block" consequence note -
  effigies gains one now, though still not as a dependency-consumable library; the boundary ADR 24
  actually decided (effigies isn't a Maven _dependency_ target) is unchanged, this just gives its
  jar a GitHub Packages home too.
- Bad, because effigies applying `maven-publish` means the root's shared Javadoc/doclint guard
  (`Xdoclint:all`/`Xwerror`) has to key on whether `withJavadocJar()` was actually called, not
  merely on `maven-publish` being applied - otherwise it would demand full doclint compliance
  across effigies' whole public API as a side effect of this decision, not a deliberate one.
  Getting effigies' code to that bar (it isn't there today) is tracked separately in `PLAN.md`.
- Bad, because tag creation staying manual keeps a publish-ordering hazard alive, mitigated by
  discipline rather than structurally prevented: if the tag is pushed before `main.yml`'s own run
  against the version-bump commit has checked out (or that run hasn't finished yet), `main.yml`'s
  `publish` job resolves the exact release version via `git describe --tags --exact-match` and
  publishes it first - leaving `release.yml`'s own publish step rejected, since a Maven release
  coordinate is normally immutable once published. The workflow's `tag` input description states
  the required ordering; nothing enforces it mechanically.
- Neutral: the `release.yml` workflow needs its own permissions - `attestations: write` and
  `id-token: write` for the attestation step, `contents: write` for the Release-asset upload,
  `packages: write` for the publish step - none of which `main.yml` needs to gain, since it stays
  unchanged.
- Neutral: root `build.gradle.kts` gains its first buildable task, its first dependency on a
  specific subproject (`project(":effigies")`, to resolve the fat jar's classpath), and its own
  `repositories { mavenCentral() }` (repositories aren't inherited between projects). Considered
  and rejected: a fourth, dedicated "packaging" subproject would have kept root a pure aggregator,
  but is disproportionate ceremony here - a new `settings.gradle.kts` include, a `DOC-MAP.md`/
  README update to the "three Gradle modules" framing - for what the root-task approach does in
  about the same number of lines it already occupied inside `effigies/build.gradle.kts`.
