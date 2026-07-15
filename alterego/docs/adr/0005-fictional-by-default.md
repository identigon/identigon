# ADR 0005: Fictional output by default

Status: accepted (2026-07-12)

## Context

Pseudonymised data leaks into test systems, demos, screenshots, and training material. If a
generated phone number, email address, or postcode happens to be real, that data can misdirect
mail, calls, or messages to a real person. Several value spaces have officially reserved or
structurally impossible regions: RFC 2606 email domains, Ofcom drama telephone ranges,
and postcode inward codes ending in letters never used (`C I K M O V`).

## Decision

Where such a region exists, the built-in generates inside it **by default**
(SPECIFICATION.md section 4.1). Opting out is explicit (`PhoneOptions.realistic()`,
`PostcodeOptions.realistic()`) and documented as reducing safety.

## Consequences

- Outputs pass format-shaped validation but fail live lookups (PAF, number allocation, MX) —
  usually exactly what pseudonymised data should do; the opt-outs exist for when realism
  matters more.
- Property tests assert range membership over large samples (fictionality tests).
- No such guarantee is possible for names, streets, cities, or organisations (each output word
  is real; only the combination is synthetic); each built-in's Javadoc states its category.
- Raw `pattern(...)` output carries no guarantee — users are pointed at the guaranteed built-ins.
