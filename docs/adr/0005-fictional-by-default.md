---
status: "accepted"
date: 2026-07-12
decision-makers: David Conneely
---

# 5. Fictional output by default, where a reserved value space exists

## Context and Problem Statement

Pseudonymised data leaks into test systems, demos, screenshots, and training material. If a
generated phone number, email address, or postcode happens to be real, that data can misdirect
mail, calls, or messages to a real person. Several value spaces have officially reserved or
structurally impossible regions: RFC 2606 email domains, Ofcom drama telephone ranges, and postcode
inward codes ending in letters never used (`C I K M O V`).

## Considered Options

- Generate freely within the real value space; let callers opt in to a fictional range explicitly.
- Generate inside the reserved/fictional range by default; let callers opt out explicitly.

## Decision Outcome

Chosen option: "generate inside the reserved/fictional range by default", because the failure mode
of forgetting to opt in (a real value silently produced) is worse than the failure mode of
forgetting to opt out (unnecessarily fictional output).

Where such a region exists, the built-in generates inside it by default (specification section
4.1). Opting out is explicit (`PhoneOptions.realistic()`, `PostcodeOptions.realistic()`) and
documented as reducing safety.

### Consequences

- Good, because outputs pass format-shaped validation but fail live lookups (PAF, number
  allocation, MX) - usually exactly what pseudonymised data should do; the opt-outs exist for when
  realism matters more.
- Good, because property tests can assert range membership over large samples (fictionality
  tests).
- Bad, because no such guarantee is possible for names, streets, cities, or organisations (each
  output word is real; only the combination is synthetic); each built-in's Javadoc states its
  category.
- Neutral: raw `pattern(...)` output carries no guarantee - users are pointed at the guaranteed
  built-ins.
