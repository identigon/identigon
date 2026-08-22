# Specification

Identigon's behaviour contract: what each of the three subprojects does, and what a caller or
operator may rely on. **This file holds none of that contract directly** — it is an index, stating
only scope and which member covers what. If you're about to write a behaviour fact *here*, it
belongs in one of the three members below instead; this file only ever names them, never
substitutes for them. The specification is a small tree, not one document, because `alterego`,
`incognito`, and `effigies` are three separately usable artifacts with three separate contracts —
even though they version and release together (see the lockstep-versioning decision in `docs/adr/`).

Requirement keywords — MUST, MUST NOT, SHOULD, SHOULD NOT, MAY — are used as defined in
[RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) where the member documents use them.

## 1. Scope

Covers the behaviour of all three subprojects that make up the `identigon` pipeline. Does not cover
build/release mechanics (Gradle wiring, lockstep versioning) — that is the concern of `docs/adr/` and
each module's own `build.gradle.kts`, not a behavioural contract.

## 2. Members

Three, one per subproject — each is the *only* place its subproject's behaviour belongs. All three
live under `docs/spec/`, the one place in `docs/` you can glob for "is this part of the contract"
(`docs/spec/*.md`) without reading this file at all:

- **[`docs/spec/alterego.md`](docs/spec/alterego.md)** — the deterministic pseudonymisation library:
  the transformation API, the built-in strategies and their fictionality guarantees, the
  determinism model, and the frozen Appendix A algorithms. Audience: consumers of `alterego`
  directly, and `incognito`'s own implementers.
- **[`docs/spec/incognito.md`](docs/spec/incognito.md)** — the database-cloning and anonymisation
  engine: the privacy model, column roles and transformation strategies, fail-closed
  classification, and the §7.3 must-not-regress invariants. Audience: consumers of `incognito`
  directly, and `effigies`' own implementers.
- **[`docs/spec/effigies.md`](docs/spec/effigies.md)** — the authoring and orchestration CLI: its
  commands, its hard invariants (metadata-only discovery, fail-closed preserved, no model in the
  engine path), and its relationship to `incognito`. Audience: CLI users.

### Which member, for a given fact

Pick by **which subproject the behaviour belongs to** — a fact about what `alterego` guarantees for
a single value is not a fact about what `incognito` guarantees for a whole database, even where one
depends on the other:

| The fact you're writing... | Goes in |
| --- | --- |
| "a `Transformation`/built-in strategy does/returns X" — a single-value guarantee | `alterego.md` |
| "the pipeline/policy/column role does X" — a whole-database guarantee | `incognito.md` |
| "the CLI command/discovery/authoring flow does X" | `effigies.md` |

A fact that seems to need two of these is usually two sentences, not one: state each subproject's
own guarantee where it belongs, and let the dependent subproject's document link back rather than
restate it (`incognito.md` already does this for `alterego`'s Appendix A, via its own
"Appendix A — `alterego` integration cheat-sheet").

## 3. Deliberately unspecified

Behaviour callers MUST NOT rely on: anything each member's own "non-goals" section names as such
(e.g. `alterego`'s non-goal of GDPR anonymisation on its own, `incognito`'s non-goals of statistical
/ analytical fidelity and formal re-identification bounds); and any behaviour explicitly deferred to
a post-v1.0 milestone in a member's own text.
