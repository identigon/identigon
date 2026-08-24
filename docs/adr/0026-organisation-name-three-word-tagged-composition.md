---
status: "accepted"
date: 2026-07-14
decision-makers: David Conneely
---

# 26. Organisation names generated as three tagged words, not a flat word pool

## Context and Problem Statement

`organisationName()` needs to compose a plausible fictional company name from a dictionary of
components. A flat, untagged pool of words risks nonsensical composition (grammatically broken or
repeated-word combinations), and two different real organisations landing on the same
pseudonymised name is a more visible artefact than a name or address collision, since real
organisation names are rarely duplicated - so the design needs a high combination count as well as
grammatical plausibility.

## Considered Options

* A flat, untagged word pool, words picked freely.
* A two-word composition (`MODIFIER` + `NOUN`, mirroring `streetAddress()`'s theme+type shape).
* A three-word tagged composition: position 1 is `MODIFIER` or `NOUN`, positions 2 and 3 are
  `NOUN` and distinct from every word already chosen.

## Decision Outcome

Chosen option: "a three-word tagged composition", because a flat pool cannot prevent nonsensical
pairings (e.g. two `MODIFIER`-shaped words next to each other), and a two-word composition does not
reach a high enough combination count to make organisation-name collisions rare.

Every dictionary entry is tagged `MODIFIER` or `NOUN` (same tagged-file mechanism as the towns
dictionary's postcode-area/nation tags -
`DictionaryWellFormedness.validateOrgComponentTags`). A name is generated as three distinct words:
`[MODIFIER-or-NOUN] + NOUN + NOUN`. This permits both real-reading patterns ("Northern Trading
Solutions", MODIFIER+NOUN+NOUN; "Trading Solutions Partners", NOUN+NOUN+NOUN) while making
same-word repeats and MODIFIER+MODIFIER pairings structurally impossible, not just unlikely. With
31 `MODIFIER` and 44 `NOUN` entries, the combination count is `44 * 43 * 73 = 138,116` -
comfortably past a 50,000-combination floor.

### Consequences

* Good, because MODIFIER+MODIFIER and same-word-repeat combinations are structurally impossible,
  not merely unlikely - no runtime check is needed to prevent them.
* Good, because the combination count (138,116) comfortably clears the collision-rarity floor this
  built-in specifically needs, unlike a two-word design.
* Neutral: `organisation-components.txt` needs the tagged-dictionary file format and its own
  well-formedness validator, the same mechanism the towns dictionary already established - not a
  new mechanism.

<!-- Extracted from alterego/docs/dictionaries.md's "Organisation-name components" section
     ("Generation algorithm, decided") during the docs/research/ migration; the full sourcing,
     mining, and filtering detail stays in docs/research/0001-alterego-dictionaries.md. -->
