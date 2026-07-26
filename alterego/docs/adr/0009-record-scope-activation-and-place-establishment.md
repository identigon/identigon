# ADR 0009: Thread-local scope activation; `isActive()` lets any built-in establish place

Status: accepted (2026-07-26)

## Context

`RecordScope.apply(transformation, value)` (section 6.1) takes only a value —
`Transformation<T>` is a plain `Function<T, T>` with no scope parameter — but the
`TransformationContext` that needs to see the scope's attributes is created several calls deeper,
inside `DefaultTransformation.apply(...)`. Threading an explicit scope parameter through that call
chain would mean changing `Transformation`'s already-published public shape (section 2.5).

Separately, `postcode()`/`phoneNumber()` establishing a *real* town's area (not an arbitrary or
fabricated one) when they run first in a scope requires telling "nothing fixed yet, but a real
scope is active" apart from "outside any scope entirely" — both look identical via `get()` alone
(empty either way). `computeIfAbsent` can't be used to probe for this cheaply either: outside a
scope it still *runs its resolver* against the live per-input randomness stream (that's what
makes it behave identically in and out of a scope), so calling it just to check would itself
consume randomness and silently perturb every subsequent draw — breaking the output-stability
guarantee (section 3.4) for every built-in that ever runs outside a scope.

## Decision

**Thread-local activation.** `DefaultRecordScope.apply(...)` installs itself on a
`ThreadLocal<DefaultRecordScope>` for the duration of the wrapped call, restoring whatever was
previously active (usually nothing) in a `finally` block. `DefaultTransformationContext.record()`
consults this thread-local when first asked, returning a view over the active scope if one exists
or the outside-scope no-op view otherwise. Both a top-level context and any `derived(...)` child
created while a scope is active pick up the same active scope automatically — they consult the
same thread-local — which is what gives composites (`fullName()`-style delegation) coherence for
free, per the section 2.2 invariant, without threading anything extra through `derived(...)`.

**`RecordAttributes.isActive()`.** A fourth method, alongside `get`/`computeIfAbsent`/`set`:
`true` inside a real scope, `false` outside any scope, costing no randomness and touching no
attribute. `postcode()`/`phoneNumber()` check it before attempting to establish the record's
place; when true and nothing is fixed yet, they call a shared `computeIfAbsent`-based helper
(`PlaceCoherence.establish`) that picks a real town from the country's dictionary and fixes both
`UK_POSTCODE_AREA` and `UK_NATION` from it (ignoring the town's name — only its tags) — so a
`city()` call afterward is guaranteed a match, not left to consume from an arbitrary or fabricated
area. `city()` itself is unaffected: its own output stays derived from its own per-input
randomness either way.

## Consequences

- No change to any already-published public signature (`Transformation`, `RecordScope`,
  `TransformationContext`) — `RecordAttributes` gains one method, which is additive.
- The thread-local is exactly as safe as ADR 0008's existing "one thread per scope" rule already
  requires: it is never consulted across threads, is fully deterministic (never time- or
  machine-dependent, restored via `finally` so it cannot leak into an unrelated later `apply()` on
  the same thread), and needs no new safety argument beyond the one `RecordScope` already makes.
- `postcode()`/`phoneNumber()` (and any future built-in with the same shape) cohere regardless of
  which of the three runs first in a scope — proven by `RecordCoherenceIntegrationTest` across
  all six orderings of city/postcode/phone.
- Establishing costs one extra `computeIfAbsent` resolution the first time any of the three is
  asked inside an active scope; outside a scope, the `isActive()` check is the only added cost
  (a boolean read), so outputs for every pre-M5 golden test stay byte-identical.
