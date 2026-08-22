---
status: "accepted"
date: 2026-07-13
decision-makers: David Conneely
---

# 6. Fixed default locale (Locale.UK), never Locale.getDefault()

## Context and Problem Statement

A builder needs some default locale. The obvious default is `Locale.getDefault()` — but that makes
output depend on the machine the code runs on: the same code with the same salt would pick
different resources on differently configured hosts, silently producing different pseudonyms,
contradicting the primary determinism goal. This library's primary deployment is UK data.

Accepted 2026-07-12, revised 2026-07-13 (accepted date reflects the revision).

## Considered Options

* Default to `Locale.getDefault()` (the JDK/platform default).
* Default to a fixed constant locale.

## Decision Outcome

Chosen option: "default to a fixed constant locale", because a fixed constant is identical on
every machine, unlike the platform default.

The builder defaults to the fixed constant `Locale.UK` (`en-GB`). `Locale.getDefault()` is banned
from all code paths. Non-UK users configure a locale explicitly; an unshipped country fails fast.

All country-scoped resources — dictionaries, postcode formats, fictional phone ranges, legal
suffixes — resolve by the locale's **country**, not its language. `en-GB` and `cy-GB` both resolve
to the same UK resources. A locale without a country fails fast; the language component steers
nothing in v1 and is reserved for future language-sensitive generation (specification section 4).
For the moment, town and street dictionary entries use English-language forms (Swansea, not
Abertawe).

### Consequences

* Good, because zero-configuration UK usage works, and output remains machine-independent because
  the default is a constant, not ambient state.
* Neutral: `en-GB` and `cy-GB` are configuration synonyms for the v1 built-ins (enforced by an
  equivalence test); `en-AU` fails fast (no AU resources) rather than silently borrowing another
  country's data.
* Bad, because a non-UK adopter who forgets to set a locale gets UK output — obvious on first
  look, and documented in the README.
