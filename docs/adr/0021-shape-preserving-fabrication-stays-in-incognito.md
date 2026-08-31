---
status: "accepted (refined by ADR-0029)"
date: 2026-08-31
decision-makers: David Conneely
---

# 21. Shape-preserving fabrication stays in Incognito, via AlterEgo's extension API

## Context and Problem Statement

ADR 15 treated `TableTransformLoadStage`'s shape-preserving character substitution (the
`DirectIdStrategy.ALTEREGO_GENERIC` and string-`SYNTHESISE` paths) as value logic that had leaked
into Incognito in violation of the delegation boundary - tracked as debt, to be migrated into
`alterego` proper.

Revisiting it: `fabricateShapePreserving(...)` is not a hand-rolled, out-of-band transformation. It
is a caller-supplied `Strategy<String>` bound through `alterEgo.bind(domain, (input, ctx) -> ...)` -
`alterego`'s own public extension mechanism. It runs on `alterego`'s salt-keyed stream
(`ctx.random()`) and inherits determinism, `unique()`, `stored()`, and record-coherence parity from
the bind. Only the ~6-line character-class walk itself is local code; value *production* happens on
`alterego`'s rails, not beside them. The earlier "tracked violation, migrate it" framing did not
distinguish this from genuinely hand-rolled substitution.

## Considered Options

* Migrate the shape-preserving logic into `alterego` as a new shared `shapePreserving(domain)`
  built-in, per ADR 15's original framing.
* Leave it in Incognito permanently, since it already runs on `alterego`'s `bind()` extension
  mechanism and is not a delegation violation.

## Decision Outcome

Chosen option: "leave it in Incognito permanently", because the boundary ADR 15 protects is *who
produces a value*, and `alterego` still produces it here - Incognito supplies only the
caller-defined strategy function, exactly as any external `alterego` consumer could. No migration
work is owed for code that already lives on the correct side of the boundary.

Shape-preserving fabrication stays in Incognito permanently, provided it continues to use
`alterego`'s `bind()` extension mechanism rather than reimplementing value production locally.

It carries **no fictionality guarantee**, and this is a settled position, not a temporary gap: a
guarantee needs a reserved or structurally-impossible value space, which by definition requires a
known format (cf. `alterego`'s blocked `companyNumber()`, which has no such space at all). An
arbitrary shape has no such space to draw from. The guarantee remains available only through the
**typed** built-in generators (`emailAddress`, `phoneNumber`, the identifier built-ins, authored
names); a policy author routes a column to one of those when the guarantee matters, not to
shape-preserving fabrication.

### Consequences

* Good, because no migration work is owed to `alterego` for this - the code already lives on the
  correct side of the delegation boundary.
* Good, because it keeps `alterego`'s built-in surface free of a `shapePreserving(domain)`
  primitive that only Incognito would use today (mentioned as a possible future promotion, not
  needed now).
* Bad, because `ALTEREGO_GENERIC` / string-`SYNTHESISE` output can, by coincidence, equal a real
  value - callers who need the fictionality guarantee must be told explicitly to route to a typed
  strategy instead; the `VerificationStage` source-value survival check is a probabilistic net over
  this, not a guarantee.
* Neutral: ADR 15's own Consequences named this as tracked debt; that characterisation is
  superseded by this record, not corrected in place (ADR 15 is `accepted` and immutable).

<!-- Originally drafted as incognito's own ADR 0009; renumbered during the doc-kit consolidation
     migration (docs/tasks/consolidate-subproject-docs.md). Mined from incognito/PLAN.md's "Phase 4
     follow-up" section, which had recorded this reversal only as a completed plan item, not as its
     own record. -->
