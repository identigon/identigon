---
status: "accepted"
date: 2026-07-12
decision-makers: David Conneely
---

# 3. Fixed value-type set instead of a public codec SPI

## Context and Problem Statement

Key derivation and mapping-store persistence need a stable canonical text form for each value. An
early draft had an open-ended public `Codec<T>` SPI, but arbitrary object graphs are not a realistic
need, and an open SPI invites non-injective or unstable encodings from clients.

## Considered Options

- An open-ended public `Codec<T>` SPI for caller-supplied types.
- A fixed set of supported types with pinned canonical encodings.

## Decision Outcome

Chosen option: "a fixed set of supported types with pinned canonical encodings", because in practice
transformations operate on strings, dates, date-times, enumerations, UUIDs, and integers, and a
fixed set lets every canonical form be reviewed for injectivity up front.

Support a fixed set of types - `String`, `Integer`, `Long`, `Boolean`, `LocalDate`, `LocalDateTime`,
`Instant`, `UUID`, and any enum - with pinned canonical encodings (the JDK `toString()` / `name()`
forms; specification section 2.6). The set mirrors what database columns typically store: text,
numbers, dates, timestamps, flags, identifiers, and coded values. Non-String types are bound with a
class token: `alterego.bind(domain, UUID.class, strategy)`. Unsupported types fail at bind time with
`AlterEgoConfigException`.

Canonical forms must be **injective**: distinct values must have distinct canonical text, otherwise
two inputs share a pseudonym and `unique()` breaks silently.

### Consequences

- Good, because there is no public codec SPI in v1 - one less abstraction to document, test, and
  freeze.
- Good, because adding further types (`LocalTime`, `YearMonth`, ...) later is a non-breaking change;
  a codec mechanism can still be added if a real need appears.
- Neutral: the encodings are part of the persistent store format and the derivation contract, frozen
  for the major version.
