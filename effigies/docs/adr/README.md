# Architecture Decision Records

Each ADR captures one significant, hard-to-reverse decision: the **context** that forced it, the
**decision** taken, and its **consequences** (including what was given up). They record *why* the
design is what it is — the reasoning that `SPECIFICATION.md` (the *what*) and `PLAN.md` (the *when*)
don't carry.

Conventions: numbered `NNNN-kebab-title.md`, never renumbered; `Status:` one of `proposed` /
`accepted` / `superseded by ADR NNNN`, with the date. A decision that reverses an ADR adds a new ADR
and marks the old one superseded rather than editing it.

| ADR | Decision |
| :--- | :--- |
| [0001](0001-authoring-above-the-engine.md) | Authoring/inference lives in Effigies, above a deterministic, model-free lib-incognito |
