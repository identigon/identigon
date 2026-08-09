# ADR 0001: Per-input key derivation

Status: accepted (2026-07-12)

## Context

Transformations are used in Java streams, including parallel streams. A shared sequential PRNG
(seeded once from the salt) would make each output depend on the input's *position* in the
stream: reordering, filtering, deduplicating, or parallelising the data would change individual
mappings, and the same value appearing in two datasets would map to two different pseudonyms.
That breaks the core promise of deterministic pseudonymisation.

## Decision

Derive a fresh 256-bit key for every input value:

```
key = HMAC-SHA256(salt, purpose || 0x00 || domain || 0x00 || canonical(input) || 0x00 || counter)
```

as specified byte-exactly in SPECIFICATION.md Appendix A.1. All randomness a strategy sees flows
from this key. The `purpose` tag separates the three uses of a derivation — randomness keys,
mapping-store keys, and keyed record-attribute resolution — so they never share a key; the
`counter` is 0 except for `unique()` collision retries.

## Consequences

- Outputs are independent of stream order and parallelism; referential integrity across datasets
  holds with no shared state.
- The salt is a secret: anyone holding it can confirm guesses about input→output pairs.
- Each element costs one HMAC computation plus a few HMAC blocks of stream output — negligible
  next to the surrounding I/O.
- The derivation message layout is frozen for the major version and enforced by conformance
  vectors.
