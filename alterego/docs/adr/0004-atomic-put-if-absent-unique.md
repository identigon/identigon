# ADR 0004: Atomic putIfAbsentUnique instead of reserve-then-put

Status: accepted (2026-07-12)

## Context

The `unique()` decorator must guarantee that distinct inputs never map to the same output. An
early draft used a two-step protocol: `reserveValue(candidate)` then `putIfAbsent(key,
candidate)`. That protocol cannot be made leak-free: a crash or a lost race between the two steps
strands a reserved value with no owner, and adding a `releaseValue` operation just moves the
problem (the releaser can also crash).

## Decision

Uniqueness is a single atomic store operation:

```java
PutUniqueResult putIfAbsentUnique(String namespace, String key, String value);
// sealed: Stored | ExistingMapping(value) | ValueTaken
```

It stores `key → value` only if the key has no mapping AND the value is unused as an output in
the namespace, atomically as a whole (SPECIFICATION.md section 5.1).

## Consequences

- No reservation state exists outside the mapping itself, so nothing can leak.
- A JDBC implementation is one transaction; the in-memory implementation is one lock or compute.
- The `unique()` retry loop is simple: `ValueTaken` → bump the derivation counter and try again.
- A reusable store contract test (M4) enforces the atomicity requirement on all implementations.
