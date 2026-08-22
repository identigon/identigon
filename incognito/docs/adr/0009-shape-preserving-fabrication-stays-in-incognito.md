# ADR 0009: Shape-preserving fabrication stays in Incognito, via AlterEgo's extension API

Status: accepted (2026-07-30, backfilled; refines ADR 0002)

## Context

ADR 0002 treated `TableTransformLoadStage`'s shape-preserving character substitution (the
`DirectIdStrategy.ALTEREGO_GENERIC` and string-`SYNTHESISE` paths) as value logic that had leaked
into Incognito in violation of the delegation boundary — tracked as debt, to be migrated into
`alterego` proper.

Revisiting it: `fabricateShapePreserving(...)` is not a hand-rolled, out-of-band transformation. It
is a caller-supplied `Strategy<String>` bound through `alterEgo.bind(domain, (input, ctx) -> …)` —
`alterego`'s own public extension mechanism (SPECIFICATION.md §7). It runs on `alterego`'s
salt-keyed stream (`ctx.random()`) and inherits determinism, `unique()`, `stored()`, and
record-coherence parity from the bind. Only the ~6-line character-class walk itself is local code;
value *production* happens on `alterego`'s rails, not beside them. The earlier "tracked violation,
migrate it" framing did not distinguish this from genuinely hand-rolled substitution.

## Decision

Shape-preserving fabrication stays in Incognito permanently, provided it continues to use
`alterego`'s `bind()` extension mechanism rather than reimplementing value production locally. This
is not a §1.4 / hard-invariant-8 violation: the boundary that ADR 0002 protects is *who produces a
value*, and `alterego` still produces it here — Incognito supplies only the caller-defined strategy
function, exactly as any external `alterego` consumer could.

It carries **no fictionality guarantee**, and this is a settled position, not a temporary gap: a
guarantee needs a reserved or structurally-impossible value space, which by definition requires a
known format (cf. `alterego`'s blocked `companyNumber()`, which has no such space at all). An
arbitrary shape has no such space to draw from. The guarantee remains available only through the
**typed** built-in generators (`emailAddress`, `phoneNumber`, the identifier built-ins, authored
names); a policy author routes a column to one of those when the guarantee matters, not to
shape-preserving fabrication.

## Consequences

- Good, because no migration work is owed to `alterego` for this — the code already lives on the
  correct side of the delegation boundary.
- Good, because it keeps `alterego`'s built-in surface free of a `shapePreserving(domain)` primitive
  that only Incognito would use today (mentioned as a possible future promotion, not needed now).
- Bad, because `ALTEREGO_GENERIC` / string-`SYNTHESISE` output can, by coincidence, equal a real
  value — callers who need the fictionality guarantee must be told explicitly to route to a typed
  strategy instead; the `VerificationStage` source-value survival check is a probabilistic net over
  this, not a guarantee.
- Neutral: ADR 0002's own Consequences section named this as tracked debt; that characterisation is
  superseded by this record, not corrected in place (ADR 0002 is `accepted` and immutable).

<!-- Mined from incognito/PLAN.md's "Phase 4 follow-up" section during the doc-kit consolidation
     migration (docs/tasks/consolidate-subproject-docs.md) — a real decision that had been recorded
     only as a completed plan item, not as its own record. -->
