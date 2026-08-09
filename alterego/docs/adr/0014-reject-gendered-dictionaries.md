# ADR 0014: Reject gendered name dictionaries

Status: accepted (2026-07-31)

## Context

A proposed feature for `lib-alterego` was "Tagged name dictionaries", which would include gendered name lists (e.g., mapping "Male" records to male fictional names and "Female" to female fictional names).

While generating gender-aligned names might seem to improve the realism of pseudonymised records, doing so natively in the library presents three major complications:

1. **API Complexity**: The library models transformations as independent operations on single values (`Transformation<T>`). To select a name based on gender, the strategy would need the `gender` column from the original record as a secondary input. This requires complex API changes (e.g., parameterised transformations like `alterego.firstName(gender)`) and forces the caller to write branching logic in their data pipeline.
2. **Inference Risks (Data Leakage)**: If pseudonymisation strictly partitions names by gender, an attacker can infer the original record's gender from the assigned fictional name. Crucially, mapping undisclosed genders ("Prefer not to say") or non-binary identities to a distinct unisex name pool inadvertently exposes these individuals to deanonymisation based purely on the output name's distribution.
3. **Fluidity and Realism**: Real-world names are increasingly fluid. Enforcing strict binary or ternary buckets for names does not reflect reality, creates arbitrary boundaries, and forces the framework into misgendering records.

## Decision

Reject the addition of gender-partitioned dictionaries and any parameterized `firstName(Gender)` API. `lib-alterego` will continue to map all names deterministically from a single, unified, gender-agnostic pool of diverse fictional names.

## Consequences

- The `Tagged name dictionaries (e.g. gendered name lists)` item is permanently removed from the Deferred list in `PLAN.md`.
- Name transformations (`firstName()`, `lastName()`, `fullName()`) remain structurally simple `Transformation<String>` implementations with no secondary input dependencies.
- Secondary data leakage regarding gender is structurally prevented by design.
- The library inherently handles all gender identities (including undisclosed and non-binary genders) gracefully without complex fallback configurations.
