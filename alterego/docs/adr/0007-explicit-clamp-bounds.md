# ADR 0007: Explicit clamp bounds; the library never reads the clock

Status: accepted (2026-07-12, revised 2026-07-13; supersedes the interim Clock-on-the-builder
design)

## Context

Constraints like "a jittered date of birth must never be in the future" need a notion of *now*.
An interim design put a `java.time.Clock` on the builder, read once at transformation-creation
time (per-element reads would break order independence across a midnight rollover). But if the
clock is read once and captured as an absolute value, the caller may as well pass that value
directly — and doing so dissolves a real ambiguity: "past" for a `LocalDate` excludes today,
while "past" for an instant means strictly before now. Only the caller knows which they mean.

## Decision

No `Clock` anywhere in the API. `JitterOptions` take explicit, **inclusive** `min(value)` /
`max(value)` bounds typed to the transformation's value type. Callers express now-relative
intent themselves:

- no future dates: `max(LocalDate.now())`
- strictly past dates (excluding today): `max(LocalDate.now().minusDays(1))`
- strictly past instants: `max(LocalDateTime.now().minusNanos(1))`

## Consequences

- The library never reads system time at all — a stronger invariant than requiring the use of
  `java.time.Clock`, and simpler to enforce (no `now()` of any kind in library code).
- Run-dependence of now-relative bounds is visible in the caller's code, where it belongs;
  fixed-bound callers get byte-reproducible runs with no special test configuration.
- The Instant-vs-LocalDate "past" semantics are the caller's explicit choice, not a library
  guess.
