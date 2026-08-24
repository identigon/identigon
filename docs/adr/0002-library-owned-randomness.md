---
status: "accepted"
date: 2026-07-12
decision-makers: David Conneely
---

# 2. Library-owned Randomness interface over an HMAC counter-mode stream

## Context and Problem Statement

Strategies need random draws. Whatever backs that draw becomes part of the output-stability
contract forever, because every pseudonym depends on its exact bit stream.

## Considered Options

* Expose `java.util.random.RandomGenerator` directly.
* Use an off-the-shelf PRNG (e.g. Mersenne Twister) internally.
* A small library-owned `Randomness` interface over an HMAC-SHA256 counter-mode stream.

## Decision Outcome

Chosen option: "a small library-owned `Randomness` interface over an HMAC-SHA256 counter-mode
stream", because the alternatives either freeze a surface this library does not control, or bring
in a dependency-or-hand-rolled PRNG that is weaker and worse-seeded than the derived key deserves.

Exposing `RandomGenerator` extends the freeze to the JDK's default methods (`nextInt(bound)`,
`doubles()`, `nextGaussian()`, ...): a JDK behaviour change or bugfix would silently change users'
pseudonymised data. An off-the-shelf PRNG such as Mersenne Twister is not in the JDK (a dependency,
or ~2.5KB of fussy state and seeding pathologies to own); it is practically seeded from 64 bits,
discarding most of the 256-bit derived key (birthday collisions near 2^32 distinct inputs would
give two different inputs identical streams); and it is cryptographically weak, muddying the
security argument.

The context exposes `Randomness` (`nextInt`, `nextLong`, `nextBoolean`, `pick`, `digit`,
`letterUpper`, `letterLower`). Its implementation is an HMAC-SHA256 counter-mode byte stream over
the full derived key, with rejection sampling, all specified byte-exactly in the specification's
Appendix A.2-A.3 and enforced by conformance vectors.

### Consequences

* Good, because the frozen compatibility surface is seven methods this library controls and
  vector-tests - no JDK or third-party algorithm is part of the output-stability contract.
* Good, because the security argument stays a one-liner: everything observable is HMAC-SHA256
  (PRF) output.
* Bad, because strategy authors cannot reach un-freezable conveniences like `nextGaussian()`.
* Neutral: clients wanting a `RandomGenerator` can adapt `nextLong` themselves; the library does
  not ship or bless an adapter.
