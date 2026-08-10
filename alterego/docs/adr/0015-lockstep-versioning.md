# ADR 0015: Lockstep versioning across the identigon monorepo, starting at 1.0.0

Status: accepted (2026-08-10)

## Context

alterego, incognito, and effigies were three separate repositories (`lib-alterego`, `lib-incognito`,
`app-effigies`), each with its own release cadence, but a strictly linear dependency chain
(effigies → incognito → alterego) and no audience for any two of them being versioned out of step
with each other. alterego was at `0.5.0-SNAPSHOT` — pre-1.0, no public API stability commitment —
while incognito was at `1.1.0` and effigies at `1.0.0`.

The three repos were merged into one monorepo, `identigon/identigon`, each subproject's full commit
history preserved via `git filter-repo --to-subdirectory-filter` rather than squashed. That surfaced
a real question: a git tag on a monorepo commit describes the whole repo's state, not one
subproject's, so three independently-drifting version numbers stop making sense at the point of
tagging a release — there is nowhere to hang three different numbers on one tag.

## Decision

Adopt lockstep versioning: one version for the whole monorepo, declared once in the root
`build.gradle.kts` and inherited by every subproject (`subprojects { version = rootProject.version
}`), not declared independently per subproject.

The first shared version is **1.0.0** — a deliberate re-baseline, not a mechanical merge of
`0.5.0`/`1.1.0`/`1.0.0`. It does not claim alterego has separately been through a 1.0 and incognito
and effigies through a 2.0; it declares this the first release of the now-unified project, with
alterego's public API considered stable as of this point. That stability call was made deliberately
for this decision, not as a side effect of the repo merge (companion decisions:
[incognito's ADR 0008](../../incognito/docs/adr/0008-lockstep-versioning.md),
[effigies' ADR 0002](../../effigies/docs/adr/0002-lockstep-versioning.md)).

Going forward, a version bump anywhere in the monorepo bumps the shared number for all three
subprojects, whether or not this subproject's own code changed in that release. A major bump means a
real breaking change somewhere in the project — it does not have to originate in alterego to trigger
one here too.

## Consequences

- alterego's independent version history (`0.1.0` through `0.5.0-SNAPSHOT`) ends here.
  `CHANGELOG.md` keeps recording what changed in this subproject specifically, but the version
  number itself is now decided monorepo-wide, not by alterego alone.
- A future breaking change to alterego alone still forces a monorepo-wide major bump, even if
  incognito and effigies are unaffected by it — the trade-off lockstep versioning makes deliberately,
  for one number to reason about and one tag per release.
- The published artifact coordinate (`org.identigon:alterego`) is unaffected; only where its version
  comes from changes.
