# ADR 0002: Library-owned Randomness interface over an HMAC counter-mode stream

Status: accepted (2026-07-12)

## Context

Strategies need random draws. Two obvious alternatives were considered and rejected:

- **Expose `java.util.random.RandomGenerator`.** Whatever sits behind the API is frozen forever,
  because every pseudonym depends on its exact bit stream. Exposing `RandomGenerator` extends
  that freeze to the JDK's default methods (`nextInt(bound)`, `doubles()`, `nextGaussian()`,
  ...): a JDK behaviour change or bugfix would silently change users' pseudonymised data.
- **Use an off-the-shelf PRNG (e.g. Mersenne Twister) internally.** MT is not in the JDK
  (dependency, or ~2.5KB of fussy state and seeding pathologies to own ourselves); it is
  practically seeded from 64 bits, discarding most of the 256-bit derived key (birthday
  collisions near 2^32 distinct inputs would give two different inputs identical streams); and
  it is cryptographically weak, muddying the security argument.

## Decision

The context exposes a small library-owned `Randomness` interface (`nextInt`, `nextLong`,
`nextBoolean`, `pick`, `digit`, `letterUpper`, `letterLower`). Its implementation is an
HMAC-SHA256 counter-mode byte stream over the full derived key, with rejection sampling, all
specified byte-exactly in SPECIFICATION.md Appendix A.2–A.3 and enforced by conformance vectors.

## Consequences

- The frozen compatibility surface is seven methods this library controls and vector-tests —
  no JDK or third-party algorithm is part of the output-stability contract.
- The security argument stays a one-liner: everything observable is HMAC-SHA256 (PRF) output.
- Strategy authors get domain-appropriate primitives and cannot reach un-freezable conveniences
  like `nextGaussian()`.
- Clients wanting a `RandomGenerator` can adapt `nextLong` themselves; the library does not ship
  or bless an adapter.
