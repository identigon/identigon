# ADR 0002: Lockstep versioning across the identigon monorepo, starting at 1.0.0

Status: accepted (2026-08-10)

## Context

alterego, incognito, and effigies were three separate repositories (`lib-alterego`, `lib-incognito`,
`app-effigies`), each with its own release cadence, but a strictly linear dependency chain
(effigies → incognito → alterego) and no audience for any two of them being versioned out of step
with each other. effigies was already at `1.0.0`, incognito at `1.1.0`, and alterego at
`0.5.0-SNAPSHOT` (pre-1.0, no public API stability commitment).

The three repos were merged into one monorepo, `identigon/identigon`, each subproject's full commit
history preserved via `git filter-repo --to-subdirectory-filter` rather than squashed. That surfaced
a real question: a git tag on a monorepo commit describes the whole repo's state, not one
subproject's, so three independently-drifting version numbers stop making sense at the point of
tagging a release — there is nowhere to hang three different numbers on one tag.

## Decision

Adopt lockstep versioning: one version for the whole monorepo, declared once in the root
`build.gradle.kts` and inherited by every subproject (`subprojects { version = rootProject.version
}`), not declared independently per subproject.

The first shared version is **1.0.0** — unchanged for effigies specifically, but now a monorepo-wide
declaration rather than this subproject's own number, and no longer independent of alterego's and
incognito's (companion decisions:
[alterego's ADR 0015](../../alterego/docs/adr/0015-lockstep-versioning.md),
[incognito's ADR 0008](../../incognito/docs/adr/0008-lockstep-versioning.md)). Going forward, a
version bump anywhere in the monorepo bumps the shared number for all three subprojects, whether or
not effigies' own code changed in that release. A major bump means a real breaking change somewhere
in the project — it does not have to originate in effigies to trigger one here too. Note that
incognito's own next major is already earmarked for the `PolicyInferrer`/`autoInfer` removal (this
subproject's [ADR 0001](0001-authoring-above-the-engine.md)); when that lands, effigies moves to
`2.0.x` along with everything else, not on its own separate cadence.

## Consequences

- effigies' independent version history — brief as it is — ends here. `CHANGELOG.md` keeps
  recording what changed in this subproject specifically, but the version number itself is now
  decided monorepo-wide, not by effigies alone.
- A future breaking change to alterego or incognito alone still forces a monorepo-wide major bump
  and a new effigies release, even when effigies itself is unchanged — the trade-off lockstep
  versioning makes deliberately, for one number to reason about and one tag per release.
- effigies has no published Maven artifact (it ships as a runnable jar, not a library), so this
  mainly affects the version reported by `effigies --version` and the release tag, not a POM
  coordinate.
