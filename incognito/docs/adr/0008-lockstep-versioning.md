# ADR 0008: Lockstep versioning across the identigon monorepo, starting at 1.0.0

Status: accepted (2026-08-10)

## Context

alterego, incognito, and effigies were three separate repositories (`lib-alterego`, `lib-incognito`,
`app-effigies`), each with its own release cadence, but a strictly linear dependency chain
(effigies → incognito → alterego) and no audience for any two of them being versioned out of step
with each other. incognito was at `1.1.0`, alterego at `0.5.0-SNAPSHOT` (pre-1.0, no public API
stability commitment), and effigies at `1.0.0`.

The three repos were merged into one monorepo, `identigon/identigon`, each subproject's full commit
history preserved via `git filter-repo --to-subdirectory-filter` rather than squashed. That surfaced
a real question: a git tag on a monorepo commit describes the whole repo's state, not one
subproject's, so three independently-drifting version numbers stop making sense at the point of
tagging a release — there is nowhere to hang three different numbers on one tag.

One thing this decision deliberately does **not** do: spend incognito's own earmarked
breaking-change release. [ADR 0002](0002-two-libraries-two-responsibilities.md)/effigies' ADR
0001 already designate incognito's *next* major as the release that removes
`PolicyInferrer`/`autoInfer` — that removal has not happened yet (the code is still here).
This re-baseline to `1.0.0` is not that release.

## Decision

Adopt lockstep versioning: one version for the whole monorepo, declared once in the root
`build.gradle.kts` and inherited by every subproject (`subprojects { version = rootProject.version
}`), not declared independently per subproject.

The first shared version is **1.0.0** — a deliberate re-baseline, not a mechanical merge of
`1.1.0`/`0.5.0`/`1.0.0`. incognito moves *backward* in number from its last independent release
(`1.1.0` → `1.0.0`); that is intentional, not a downgrade — it marks the version series restarting
under the unified project rather than continuing incognito's old, now-retired standalone numbering
(companion decisions: [alterego's ADR 0015](../../alterego/docs/adr/0015-lockstep-versioning.md),
[effigies' ADR 0002](../../effigies/docs/adr/0002-lockstep-versioning.md)).

Going forward, a version bump anywhere in the monorepo bumps the shared number for all three
subprojects, whether or not this subproject's own code changed in that release. A major bump means a
real breaking change somewhere in the project. The `PolicyInferrer`/`autoInfer` removal remains the
trigger for the *next* major once it lands — this ADR does not pre-empt it.

## Consequences

- incognito's independent version history (`1.1.0` and earlier) ends here. `CHANGELOG.md` keeps
  recording what changed in this subproject specifically, but the version number itself is now
  decided monorepo-wide, not by incognito alone.
- Anyone tracking incognito's version number specifically will see it go `1.1.0 → 1.0.0` at this
  commit — a one-time, deliberate anomaly, not a pattern; documented here so it isn't mistaken for
  a mistake.
- A future breaking change to incognito alone (including the deferred `autoInfer` removal) still
  forces a monorepo-wide major bump, even if alterego and effigies are unaffected by it — the
  trade-off lockstep versioning makes deliberately, for one number to reason about and one tag per
  release.
- The published artifact coordinate (`org.identigon:incognito`) is unaffected; only where its
  version comes from changes.
