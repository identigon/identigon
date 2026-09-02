# Identigon - Instructions for Implementation Agents

**Read [`DOC-MAP.md`](DOC-MAP.md) before writing anything down.** It says which file a given fact
belongs in - root `SPECIFICATION.md` indexes `docs/spec/`, decisions live in `docs/adr/`, and so on.
This page is behavioural rules; the map decides where facts go.

Identigon is a monorepo of three subprojects forming a pipeline, each a sibling Gradle project of
the others (`effigies` -> `incognito` -> `alterego`, via `project(...)` dependencies):

- `alterego/` - a zero-dependency Java 25 library for deterministic pseudonymisation.
- `incognito/` - a Java 25 library that clones a production database into a schema-identical test
  database with all PII replaced by clearly fictional data, preserving data volumes and
  inter-entity relationships. Delegates all field-value fabrication to `alterego`.
- `effigies/` - a Java 25 authoring and orchestration CLI frontend that sits above `incognito`. It
  discovers schemas, scaffolds declarative `policy.yaml` files, and drives the engine to anonymise
  databases.

The root `PLAN.md` tracks the whole monorepo's backlog (each entry optionally tagged
`**Project:**`); root `CHANGELOG.md` covers every release, pre-1.0.0 history included
(project-prefixed version tags for each subproject's own past releases, then every shared release
from `1.0.0` onward, entries tagged by subproject under Keep a Changelog's own categories). Each
subproject's specification now lives under root `docs/spec/` - read the relevant one(s) before
writing any code, for whichever subproject(s) you're touching:

**This also covers `identigon.github.io`**, the separate repository holding the project's public
site: its decisions live in this repo's `docs/adr/`, and its backlog in this `PLAN.md` (tagged
`identigon.github.io`), rather than in a documentation structure of its own - see
`docs/adr/0033-extend-documentation-coverage-to-identigon-github-io.md`. Working in that repo?
Its own `AGENTS.md` there covers site-specific hazards only and routes back here for the rest.

## alterego/

- `docs/spec/alterego.md` - the behavioural contract, including the normative Appendix A. Every
  observable behaviour is defined there; if you find a gap, flag it - do not invent behaviour.
- `docs/tasks/alterego-M<n>.md` - if present, the ordered checklist for the current milestone. Work
  top to bottom, ticking items off as you complete them, and delete the file once the milestone
  ships - its history lives in git, not as a lingering doc.

## incognito/

- `docs/spec/incognito.md` - the behavioural contract (privacy model, roles, strategies, the §7.3
  must-not-regress invariants). Every observable behaviour is defined there; if you find a gap, flag
  it - do not invent behaviour.

## effigies/

- `docs/spec/effigies.md` - the behavioural contract. Every observable behaviour is defined there;
  if you find a gap, flag it - do not invent behaviour.

Decisions for all three subprojects live together in root `docs/adr/` (numbered across the whole
monorepo, not per subproject) - decisions already made, with reasons. Do not revisit or "improve"
one; supersede with a new ADR if one genuinely changes.

## Documentation

When documents disagree, tense settles it:

- `docs/spec/<subproject>.md` - present tense, authoritative about what that subproject does now
  (indexed from root `SPECIFICATION.md`).
- `CHANGELOG.md` - past tense. What changed, never what is true today.
- `PLAN.md` - intent. Nothing described in it exists yet.
- `docs/adr/` - why. Only `accepted` records bind; check the status before relying on one.

Before you edit:

- A specification member follows the work. Change it because behaviour changed, not because it
  would read better. Its purpose and scope are not yours to revise.
- Never change an ADR's `status`, and never edit one that says `accepted`. Drafting a record is
  yours; deciding one is not - leave `decision-makers` as the template's placeholder too.
- Delete completed `PLAN.md` entries rather than marking them done.
- Adding a document means updating `DOC-MAP.md` in the same commit.

## Build and test

```sh
./gradlew build              # compile + all tests (+ spotbugs/pmd/javadoc where applicable) for
                              # all three subprojects, in dependency order; must be green before
                              # finishing any task
./gradlew test                # tests only, all subprojects
./gradlew :alterego:build     # scope any task to a single subproject with :name:
./gradlew :incognito:test
./gradlew javadoc             # alterego and incognito only; must complete without warnings
./gradlew publishToMavenLocal # alterego/incognito binary + sources + javadoc jars and a
                               # complete POM, for external Maven consumers
```

- `alterego` and `incognito` are sibling Gradle subprojects here (`project(":alterego")` /
  `project(":incognito")`), not external Maven coordinates - a plain `./gradlew build` at the root
  builds them in dependency order automatically; no separate `publishToMavenLocal` round-trip is
  needed for local development.
- `incognito`'s integration tests use Testcontainers and **require Docker**; they skip gracefully
  where Docker is unavailable. On Docker Engine 29.x set `TESTCONTAINERS_RYUK_DISABLED=true`.

## Definition of done (every task)

- `./gradlew build` is green (compile, tests, and whichever of spotbugs/pmd/jacoco/javadoc apply
  to the subproject(s) touched).
- New or changed public API exists only where a specification section (in `docs/spec/`) defines it.
- **Update the root `CHANGELOG.md` under `## [Unreleased]`, staged in the same diff as the change -
  before considering the task done, not as a separate follow-up.** Covers fixes and test-coverage
  work, not just new capabilities: if someone upgrading would want to know about it, it gets an
  entry. Only document what actually shipped - an approach tried and reverted before landing gets no
  entry.
- **alterego**: behaviour pinned in Appendix A is covered by a conformance test against the frozen
  vectors. Public types and methods have Javadoc that states behaviour, not implementation.
- **incognito**: public types and methods have Javadoc that states behaviour, not implementation -
  the doclint `missing` category is enforced (full `Xdoclint:all`): every public element needs a
  doc comment and the appropriate `@param`/`@return`/`@throws`, or the build fails. The §7.3
  invariants hold, and any new behaviour is covered by a test.

## Hard invariants - never violate

### alterego

1. Never change the Appendix A algorithms, the canonical encodings (spec section 2.6), the
   built-in domain names (`"alterego:..."`), or the built-in attribute-key names (spec section
   6.3 - they feed keyed-scope derivation). Changing any of them silently changes every user's
   pseudonymised data.
2. Never regenerate, edit, or delete frozen test vectors under
   `alterego/src/test/resources/vectors/`. If an implementation disagrees with a vector, the
   implementation is wrong.
3. No runtime dependencies. The JDK (including `javax.crypto`) only. Test scope may use JUnit
   Jupiter, and AssertJ if fluent assertions are wanted; no property-based-testing framework
   (jqwik was removed - see the matching ADR). Property-style tests are plain JUnit loops over
   deterministically enumerated inputs.
4. `java.util.random.RandomGenerator` must not appear anywhere in the public API, and no
   off-the-shelf PRNG may replace the HMAC counter-mode stream (see the library-owned-randomness
   ADR).
5. Nothing machine- or time-dependent anywhere in library code: no `Locale.getDefault()`, no
   system time of any kind (`System.currentTimeMillis()`, `Instant.now()`, `LocalDate.now()`,
   `Clock`), no `new Random()` or `UUID.randomUUID()`, no iteration over unordered collections
   where order reaches an output. Time-relative constraints are explicit caller-supplied bounds
   (see the explicit-clamp-bounds ADR).
6. If a change would alter any golden output, stop and flag it - that is a breaking change
   requiring an independent decision, whatever the reason.
7. Do not skip, disable or weaken tests to get a green build.

### incognito

1. **Fail-closed classification.** An unclassified column aborts the run; auto-inference only
   _suggests_ roles, never assigns them. A `SENSITIVE` column with no `distinguishing` declaration
   fails. Never copy a column you were not told how to handle (SPEC §7.2, and the matching ADR).
2. **No `hashCode()`-derived fabricated values or jitter deltas.** Every fabricated value and
   every jitter delta derives from `alterego`'s salt-keyed HMAC stream (SPEC §5.1, and the matching
   ADR).
3. **The salt and row values are never logged**, and the salt is destroyed on completion. The
   library performs no logging today; if it ever does, use the JDK `System.Logger` facade and
   emit only coarse operational events - never the salt, never a field value (SPEC §5.1/§7.3).
4. **Session settings on the insert connection only.** `session_replication_role='replica'` (and
   any per-session state) is set on the same connection that performs the inserts (SPEC §9).
5. **No `shiftDate(YEAR)` for a strongly-identifying date** (e.g. `dob`) - use wide jitter or
   synthesise (Appendix B).
6. **No silent skipping** of tables or columns (cyclic, unclassified, untransformable): fail loud
   or surface it in the `AnonymisationReport` - never drop.
7. **`pg_stats` is an optimisation, never the privacy gate.** Keep-vs-fabricate is the
   `distinguishing` declaration alone (SPEC §4.1, and the matching ADR).
8. **Incognito delegates value transformation to `alterego`.** Do not hand-roll redaction,
   anonymisation, substitution, or format-preserving generation here; add the primitive to
   AlterEgo and call it (SPEC §1.4, and the matching ADR). Existing violations are tracked bugs, not
   licence to add more.

### effigies

1. **Metadata only.** Discovery and every emitted artifact carry schema metadata, never sampled
   real data values.
2. **Fail-closed preserved.** Effigies suggests roles; it never assigns one behind the user's back.
3. **No model in the engine path.** Inference is authoring; the anonymisation run is a
   deterministic, model-free `incognito` execution.
4. **No secrets in files.** Credentials and salt parameters come from CLI args or environment
   variables, never written to a committed config.

## Code style

- Idiomatic Java 25: records, sealed interfaces, pattern matching. Match the surrounding code's
  conventions, comment density, and naming.
- Wrap markdown files at column 100.
- **alterego** additionally follows Google Java Style, with two exceptions: empty blocks may be on
  one line (e.g. `record Stored() implements PutUniqueResult {}`), and implementation records
  inside a sealed interface need no blank lines between them. Built-in strategies are
  package-private; users reach them only through `AlterEgo` factory methods.

## Git

- Never run `git commit` or `git push` unless explicitly asked. Present the changes and a
  suggested commit message, then wait.
