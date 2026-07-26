# ADR 0011: File-backed mapping store in core (append-only log, single process)

Status: accepted (2026-07-26)

## Context

`stored()` and `unique()` promise cross-run stability: mappings, and in particular `unique()`
collision resolutions, are "recorded in the mapping store, so it stays stable on every later
run" (spec 5.3, README). The only store shipped in v0.1.0 is `InMemoryMappingStore`, which does
not survive the process — so the promise was only achievable by users writing their own
`MappingStore`. The zero-runtime-dependency invariant rules out shipping a JDBC/SQLite/Redis
store in the core artifact.

## Decision

Ship `FileMappingStore` in the core library (spec 5.4): a persistent `MappingStore` backed by a
single local file, implemented with the JDK only.

- **Append-only log, replayed into memory on open.** Mappings are permanent by contract
  (put-if-absent semantics, never updated or deleted), so a log needs no compaction and the file
  grows by exactly one line per distinct stored mapping — the same asymptotic footprint as the
  in-memory store. Replay rebuilds the same forward-map-plus-inverse-index structure the
  in-memory store uses, so read and uniqueness-check performance are identical after open.
- **Single-process, enforced by an exclusive `FileChannel` lock** held from `open` to `close`.
  Cross-process put-if-absent atomicity over a shared flat file cannot be made reliable and
  portable with the JDK alone; rather than a store that is subtly unsafe when shared, sharing is
  rejected at `open` time. Multi-process pipelines belong on an external store (JDBC module,
  deferred).
- **Text format, frozen** (header line plus one tab-separated record per mapping, key and value
  base64url-encoded; spec 5.4). The file is persistent user data, exactly like the A.4 hex key
  encoding: the format is versioned by its header line and never changes within a major version.
  Keys and values are always base64url-encoded because raw mapping keys (`rawMappingKeys(true)`)
  can contain any character; the namespace is charset-constrained by section 2.6 and written
  verbatim.
- **Flush-per-write, no fsync.** Success is reported only after the record is written and
  flushed to the OS, so a process crash cannot lose an acknowledged mapping. An OS/power crash
  can lose a tail record; the torn-tail rule (ignore and overwrite an unterminated final line)
  makes reopening safe, and an unacknowledged write never had callers relying on it. Interior
  corruption or duplicate keys fail `open` loudly — the store never silently repairs data.

## Consequences

- The cross-run stability promises of `stored()`/`unique()` are now achievable out of the box;
  README and spec examples can use a real persistent store.
- The file format joins the frozen persistent contract (spec 3.4 discipline applies: a change to
  it is a breaking change to users' stored data).
- The store contract test (section 10) runs against `FileMappingStore` as well as
  `InMemoryMappingStore`.
- `open` throwing on a held lock means two `AlterEgo` instances in one process cannot share one
  store file via separate `open` calls — they share the single `FileMappingStore` instance
  instead (it is thread-safe). Documented in Javadoc.
- JDBC- and Redis-backed stores remain deferred (`PLAN.md`) as separate artifacts.
