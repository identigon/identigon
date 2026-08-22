---
status: "accepted"
date: 2026-07-31
decision-makers: David Conneely
---

# 22. Reject gendered name dictionaries

## Context and Problem Statement

A proposed feature for `alterego` was "Tagged name dictionaries", which would include gendered
name lists (e.g., mapping "Male" records to male fictional names and "Female" to female fictional
names).

While generating gender-aligned names might seem to improve the realism of pseudonymised records,
doing so natively in the library presents three major complications:

1. **API complexity**: the library models transformations as independent operations on single
   values (`Transformation<T>`). To select a name based on gender, the strategy would need the
   `gender` column from the original record as a secondary input. This requires complex API
   changes (e.g., parameterised transformations like `alterego.firstName(gender)`) and forces the
   caller to write branching logic in their data pipeline.
2. **Inference risk (data leakage)**: if pseudonymisation strictly partitions names by gender, an
   attacker can infer the original record's gender from the assigned fictional name. Crucially,
   mapping undisclosed genders ("prefer not to say") or non-binary identities to a distinct unisex
   name pool inadvertently exposes these individuals to deanonymisation based purely on the output
   name's distribution.
3. **Fluidity and realism**: real-world names are increasingly fluid. Enforcing strict binary or
   ternary buckets for names does not reflect reality, creates arbitrary boundaries, and forces the
   framework into misgendering records.

## Considered Options

* Add gender-partitioned name dictionaries and a parameterised `firstName(Gender)` API.
* Reject the feature; keep a single, unified, gender-agnostic name pool.

## Decision Outcome

Chosen option: "reject the feature; keep a single, unified, gender-agnostic name pool", because
gender-partitioned output creates a data-leakage path of its own (inferring the source gender from
the output name), on top of the API complexity and the fluidity mismatch.

`alterego` will continue to map all names deterministically from a single, unified, gender-agnostic
pool of diverse fictional names.

### Consequences

* Good, because name transformations (`firstName()`, `lastName()`, `fullName()`) remain
  structurally simple `Transformation<String>` implementations with no secondary input
  dependencies.
* Good, because secondary data leakage regarding gender is structurally prevented by design.
* Good, because the library inherently handles all gender identities (including undisclosed and
  non-binary genders) gracefully without complex fallback configurations.
* Neutral: the "tagged name dictionaries" item is permanently removed from consideration rather
  than deferred.
