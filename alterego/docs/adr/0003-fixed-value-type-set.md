# ADR 0003: Fixed value-type set instead of a public codec SPI

Status: accepted (2026-07-12)

## Context

Key derivation and mapping-store persistence need a stable canonical text form for each value.
An early draft had an open-ended public `Codec<T>` SPI. In practice, transformations operate on
strings, dates, date-times, enumerations, UUIDs, and integers; arbitrary object graphs are not a
realistic need, and an open SPI invites non-injective or unstable encodings from clients.

## Decision

Support a fixed set of types — `String`, `Integer`, `Long`, `Boolean`, `LocalDate`,
`LocalDateTime`, `Instant`, `UUID`, and any enum — with pinned canonical encodings (the JDK
`toString()` / `name()` forms; SPECIFICATION.md section 2.6). The set mirrors what database
columns typically store: text, numbers, dates, timestamps, flags, identifiers, and coded values. Non-String types are bound with a class token:
`alterego.bind(domain, UUID.class, strategy)`. Unsupported types fail at bind time with
`AlterEgoConfigException`.

Canonical forms must be **injective**: distinct values must have distinct canonical text,
otherwise two inputs share a pseudonym and `unique()` breaks silently.

## Consequences

- No public codec SPI in v1; one less abstraction to document, test, and freeze.
- Adding further types (`LocalTime`, `YearMonth`, ...) later is a non-breaking change; a codec
  mechanism can still be added if a real need appears.
- The encodings are part of the persistent store format and the derivation contract, frozen for
  the major version.
