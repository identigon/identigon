---
status: "accepted"
date: 2026-08-10
decision-makers: David Conneely
---

# 24. Lockstep versioning across the identigon monorepo, starting at 1.0.0

## Context and Problem Statement

`alterego`, `incognito`, and `effigies` were three separate repositories (`lib-alterego`,
`lib-incognito`, `app-effigies`), each with its own release cadence, but a strictly linear
dependency chain (effigies -> incognito -> alterego) and no audience for any two of them being
versioned out of step with each other. `alterego` was at `0.5.0-SNAPSHOT` (pre-1.0, no public API
stability commitment), `incognito` was at `1.1.0`, and `effigies` was at `1.0.0`.

The three repos were merged into one monorepo, `identigon/identigon`, each subproject's full commit
history preserved via `git filter-repo --to-subdirectory-filter` rather than squashed. That surfaced
a real question: a git tag on a monorepo commit describes the whole repo's state, not one
subproject's, so three independently-drifting version numbers stop making sense at the point of
tagging a release - there is nowhere to hang three different numbers on one tag.

One thing this decision deliberately does **not** do: spend `incognito`'s own earmarked
breaking-change release. ADR 15 / `effigies`' ADR 23 already designate `incognito`'s _next_ major as
the release that removes `PolicyInferrer`/`autoInfer` - that removal had not happened yet at the
time of this decision (the code was still present). This re-baseline to `1.0.0` is not that release.

## Considered Options

- Keep each subproject independently versioned, now inside one monorepo.
- Adopt lockstep versioning: one shared version for the whole monorepo, re-baselined to `1.0.0`.

## Decision Outcome

Chosen option: "adopt lockstep versioning, re-baselined to `1.0.0`", because a single monorepo tag
has nowhere to hang three independently-drifting version numbers, and a strictly linear dependency
chain gives no audience for versioning any two of the three out of step with each other.

Adopt lockstep versioning: one version for the whole monorepo, declared once in the root
`build.gradle.kts` and inherited by every subproject
(`subprojects { version = rootProject.version }`), not declared independently per subproject.

The first shared version is **1.0.0** - a deliberate re-baseline, not a mechanical merge of
`0.5.0`/`1.1.0`/`1.0.0`. It does not claim `alterego` has separately been through a 1.0 and
`incognito`/`effigies` through a 2.0; it declares this the first release of the now-unified project,
with `alterego`'s public API considered stable as of this point. That stability call was made
deliberately for this decision, not as a side effect of the repo merge. `incognito` moves _backward_
in number from its last independent release (`1.1.0` -> `1.0.0`); that is intentional, not a
downgrade - it marks the version series restarting under the unified project rather than continuing
`incognito`'s old, now-retired standalone numbering. `effigies`' own number is unchanged (`1.0.0` ->
`1.0.0`) but is now a monorepo-wide declaration rather than an independent one.

Going forward, a version bump anywhere in the monorepo bumps the shared number for all three
subprojects, whether or not a given subproject's own code changed in that release. A major bump
means a real breaking change somewhere in the project - it does not have to originate in any one
particular subproject to trigger one for all three. `incognito`'s `PolicyInferrer`/`autoInfer`
removal remains the trigger for the _next_ major once it lands; this ADR does not pre-empt it.

### Consequences

- Good, because from this point there is exactly one number to reason about and one tag per release,
  matching the reality of a strictly linear dependency chain with no independent release audience.
- Bad, because each subproject's independent version history ends here - `alterego`'s
  `0.1.0`-`0.5.0-SNAPSHOT`, `incognito`'s history up to `1.1.0`, `effigies`' brief standalone
  history. The root `CHANGELOG.md` keeps recording what changed in each subproject specifically
  (project-prefixed pre-1.0.0 entries, then grouped-by-subproject sections per shared release), but
  the version number itself is now decided monorepo-wide, not by any one subproject alone.
- Neutral: anyone tracking `incognito`'s version number specifically will see it go `1.1.0 -> 1.0.0`
  at this commit - a one-time, deliberate anomaly, not a pattern; documented here so it isn't
  mistaken for a mistake.
- Bad, because a future breaking change to any one subproject alone still forces a monorepo-wide
  major bump and a new release for all three, even when the other two are themselves unchanged - the
  trade-off lockstep versioning makes deliberately.
- Neutral: the published artifact coordinates (`org.identigon:alterego`, `org.identigon:incognito`)
  are unaffected; only where their version comes from changes. `effigies` has no published Maven
  artifact (it ships as a runnable jar, not a library), so this mainly affects the version reported
  by `effigies --version` and the release tag.

<!-- Merged from three near-identical companion records - alterego's ADR 0015, incognito's ADR
     0008, effigies' ADR 0002 - during the doc-kit consolidation migration
     (docs/tasks/consolidate-subproject-docs.md). All three recorded the same decision, on the same
     date, from each subproject's own vantage point; this is the single merged record. -->
