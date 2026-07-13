# ADR 0006: Fixed default locale (Locale.UK), never Locale.getDefault()

Status: accepted (2026-07-12, revised 2026-07-13)

## Context

The dangerous builder default is `Locale.getDefault()`: it makes output depend on the machine the
code runs on — the same code with the same salt would pick different resources on differently
configured hosts, silently producing different pseudonyms, contradicting the primary determinism
goal. A *fixed constant* default has no such problem: it is identical on every machine. This
library's primary deployment is UK data.

## Decision

The builder defaults to the fixed constant `Locale.UK` (`en-GB`). `Locale.getDefault()` is banned
from all code paths (see CLAUDE.md invariants). Non-GB users configure a locale explicitly; an
unshipped country fails fast.

All country-scoped resources — dictionaries, postcode formats, fictional phone ranges, legal
suffixes — resolve by the locale's **country**, never its language: a Welsh-language dataset is
GB-wide data, not Wales-located data, just as `en-GB` implies the English language, not an
England location. A locale without a country fails fast; the language component steers nothing
in v1 and is reserved for future language-sensitive generation (SPECIFICATION.md section 4).
Town and street dictionary entries use English-language forms (Swansea, not Abertawe), matching
what data stores typically hold.

## Consequences

- Zero-configuration UK usage; output remains machine-independent because the default is a
  constant, not ambient state.
- `en-GB` and `cy-GB` are configuration synonyms for the v1 built-ins (enforced by an
  equivalence test); `en-AU` fails fast (no AU resources) rather than silently borrowing another
  country's data.
- A non-UK adopter who forgets to set a locale gets GB output — obvious on first look, and
  documented in the README.
