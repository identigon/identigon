# ADR 0008: Record coherence via RecordScope, separate from the MappingStore

Status: accepted (2026-07-13)

## Context

Transforming a record's fields independently can produce incoherent combinations (Manchester +
a London postcode + a London phone number; a Companies House `SC...` prefix on an English
record). Related fields need shared state with a defined scope. Two candidate homes were
considered:

- **The `MappingStore` SPI** — rejected. The store is persistent, cross-record, pluggable
  key→value state; record coherence is ephemeral, intra-record state that dies with the record.
  Forcing it through the store would demand record keys for every record, pollute persistence
  with transient data, and drag thread-safety and lifetime questions into every store
  implementation.
- **A new pluggable SPI** — rejected. Nothing about intra-record state needs to be pluggable;
  it is a plain in-memory object with the scope's lifetime.

## Decision

A `RecordScope` (`try (var rec = alterego.record()) { ... }`, or `alterego.record(key)` for a
keyed scope) bounds one record. It holds typed attributes (`AttributeKey<A>`), reached from any
strategy via `context.record()`, with **first-touch-wins** semantics: whichever field is
transformed first fixes shared attributes (e.g. `GB_POSTCODE_AREA`, `GB_COUNTRY`) and later
fields follow. Conflicting `set` throws `AlterEgoCoherenceException`. Keyed scopes derive
`computeIfAbsent` randomness from the record key and attribute name (purpose
`alterego/1/record`), making resolved attributes independent of field order.

Outside any scope, the same strategy code runs unchanged: `get` is empty, `set` is discarded,
`computeIfAbsent` resolves without retaining — fields stay independent.

## Consequences

- Coherence is opt-in per record and invisible to strategies that do not need it.
- Within a scope, reproducibility requires a stable field order (or a keyed scope for purely
  resolved attributes); documented.
- Fictionality guarantees are unaffected: coherence steers *which* fictional value is chosen
  (e.g. a London drama range), never whether it is fictional.
- Stored/unique mappings predate record attributes and are returned as-is; strict coherence
  plus persistent mappings requires the caller to scope the store deliberately. Documented
  caveat.
- Scopes are single-threaded and per-record; parallel record streams create one scope per
  element.
