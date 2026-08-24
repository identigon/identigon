---
status: "accepted"
date: 2026-07-12
decision-makers: David Conneely
---

# 4. Atomic putIfAbsentUnique instead of reserve-then-put

## Context and Problem Statement

The `unique()` decorator must guarantee that distinct inputs never map to the same output. An
early draft used a two-step protocol: `reserveValue(candidate)` then `putIfAbsent(key, candidate)`.
That protocol cannot be made leak-free: a crash or a lost race between the two steps strands a
reserved value with no owner, and adding a `releaseValue` operation just moves the problem (the
releaser can also crash).

## Considered Options

* A two-step reserve-then-put protocol, plus a release operation for the failure case.
* A single atomic store operation covering both the uniqueness check and the write.

## Decision Outcome

Chosen option: "a single atomic store operation", because it is the only shape with no reservation
state that can be left owner-less by a crash or a lost race.

```java
PutUniqueResult putIfAbsentUnique(String namespace, String key, String value);
// sealed: Stored | ExistingMapping(value) | ValueTaken
```

It stores `key -> value` only if the key has no mapping AND the value is unused as an output in the
namespace, atomically as a whole (specification section 5.1).

### Consequences

* Good, because no reservation state exists outside the mapping itself, so nothing can leak.
* Good, because a JDBC implementation is one transaction; the in-memory implementation is one lock
  or compute.
* Neutral: the `unique()` retry loop is simple: `ValueTaken` -> bump the derivation counter and try
  again.
* Neutral: a reusable store contract test enforces the atomicity requirement on all
  implementations.
