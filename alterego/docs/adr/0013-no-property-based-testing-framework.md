# ADR 0013: No property-based-testing framework; jqwik removed

Status: accepted (2026-07-26)

## Context

The test suite used jqwik (`net.jqwik:jqwik`, test scope) for a handful of `@Property`
generative tests. In May 2026 the jqwik maintainer shipped a prompt-injection payload in
release 1.10.0: text written to stdout during test runs, ANSI-escaped to be invisible in a
human terminal but readable by an AI agent, instructing it to delete the caller's tests and
code. Release 1.10.1 softened the wording and made the ANSI hiding opt-in, and the maintainer
stated the project is not intended for use by AI coding agents at all.

AlterEgo was pinned to the older, clean 1.9.1, so it was never exposed. But the dependency is a
standing liability: CLAUDE.md explicitly permitted jqwik, this repository's later milestones are
implemented by AI agents, and a routine version bump would reintroduce the payload. The value
jqwik provided here was small — six `@Property` methods with simple generators (bounded ints,
strings, string lists) and no reliance on shrinking to find rare defects.

## Decision

Remove jqwik entirely and adopt **no** property-based-testing framework. Property-style tests are
plain JUnit Jupiter tests that loop over a deterministically enumerated set of inputs (a fixed,
varied list, or a generated `"input-" + i` range), asserting the invariant for each.

- **Deterministic enumeration, not a seeded PRNG.** A fixed input set reproduces identically on
  every run with no seed to capture or report, and matches the style already used elsewhere in
  the suite (e.g. `FictionalityTest`, the parallel-stream tests). The curated lists deliberately
  include edge cases jqwik's default string generation covered — empty string, whitespace,
  punctuation, mixed case, non-ASCII, emoji.
- Allowed test dependencies are now JUnit Jupiter, plus AssertJ if fluent assertions are ever
  wanted. Any property-based-testing framework (jqwik, QuickTheories, or another) is out —
  re-adding one needs a new ADR superseding this.

QuickTheories (Apache-2.0) was considered as a like-for-like replacement that keeps shrinking,
and rejected: for six simple invariants the shrinking is not worth a new dependency, and
dropping the framework aligns with the project's zero-dependency ethos.

## Consequences

- No dependency change reaches the published artifact: jqwik was test-scope only and never in
  the POM, so consumers are unaffected. No CHANGELOG entry.
- CLAUDE.md hard invariant #3 updated to permit JUnit Jupiter (and AssertJ), and to forbid a
  property-based-testing framework, pointing here.
- Lost capability: automatic input shrinking to a minimal failing case. Accepted — the converted
  tests assert shape/range/stability invariants where the failing input is already obvious from
  the assertion message, not deep search properties where shrinking earns its keep.
- Future feature milestones (M7, M8, ...) add property-style coverage as JUnit loops, not
  `@Property` methods.
