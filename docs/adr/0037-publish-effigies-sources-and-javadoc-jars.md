---
status: "proposed"
date: 2026-09-26
decision-makers: {who decided - required once the status is not "proposed"}
---

# 37. Publish effigies with sources and javadoc jars like the other modules

## Context and Problem Statement

ADR-0028 published effigies' thin jar with no sources or javadoc jars, unlike `alterego` and
`incognito`. Maven Central requires both for every artifact, and a per-module difference in
publication shape kept the publishing configuration duplicated in three build scripts instead of
defined once at the root.

## Considered Options

- Keep effigies without sources/javadoc jars (ADR-0028 as written)
- Publish all three modules with the same shape: binary, sources and javadoc jars, signed when a key
  is supplied

## Decision Outcome

Chosen option: "Publish all three modules with the same shape", because it makes effigies
Central-ready and lets the root build define publishing once for every module.

This refines ADR-0028 only on the sources/javadoc point; the `standalone` classifier for the fat jar
and the Release asset set are unchanged.

### Consequences

- Good, because the publication shape is uniform and defined in one place.
- Good, because effigies meets Maven Central's artifact requirements.
- Bad, because effigies' public API (`EffigiesCli`) is tiny, so its javadoc jar carries little.
