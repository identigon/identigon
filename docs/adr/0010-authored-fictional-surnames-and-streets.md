---
status: "accepted"
date: 2026-07-26
decision-makers: David Conneely
---

# 10. Authored, obviously-fictional surnames and street names, across all countries

## Context and Problem Statement

`lastName()` and `streetAddress()` drew from real UK population/vocabulary data (ONS/NRS surnames;
street-name components ultimately from OS Open Names frequency data). ADR 5's fictional-by-default
guarantee explicitly excluded these - every output word was real, so only the combination was
synthetic, and no structural or reserved-value-space guarantee was available the way one exists for
phone numbers, email domains, and postcodes.

## Considered Options

- Gate "obviously fictional" behind a separate, wholly invented locale.
- Draw from authored, deliberately fictional vocabulary within the existing real-country locales.

## Decision Outcome

Chosen option: "draw from authored, deliberately fictional vocabulary within the existing
real-country locales", because a separate fictional locale conflates two orthogonal concerns
(country-specific format vs. whether the content is real) into one axis, and would need a parallel
fictional locale for every real one as more countries are added.

`lastName()` and `streetAddress()` draw from authored, not sourced, vocabulary - words built to read
as unmistakably fictional at a glance, not merely plausible-shaped (surnames like "Testperson",
"Sampleford"; street themes like "Example", "Nonexistent", combined with the existing real
structural type words: "Example Road", "Nonexistent Avenue") - reviewed to avoid coinciding with a
known real surname or street name. This is a per-category policy, not a per-locale one: any future
country's `lastName()`/`streetAddress()` dictionaries are authored the same way, keeping the
guarantee uniform as more countries are added, rather than reintroducing the locale-proliferation
problem above.

`firstName()`, `city()`, `organisationName()`, and `postcode()`'s outward code are unaffected - they
keep drawing from real data/vocabulary. `postcode()`'s existing impossible-inward-code-letter
guarantee (ADR 5) is also unaffected: it is a genuine guarantee (structurally undeliverable), just
not one a casual reader would recognise unprompted, and is kept as-is rather than redesigned to also
be visually obvious.

### Consequences

- Good, because `fullName()` now pairs a real first name with an obviously-fictional surname (e.g.
  "Alice Testperson") - a deliberate asymmetry: first names are common enough on their own not to
  identify anyone, so only the surname needs to make the full name unmistakably not a real person's.
- Bad, because `lastName()`/`streetAddress()` join `emailAddress()`/`phoneNumber()`/`postcode()` in
  the fictional-by-default table via a weaker _kind_ of guarantee than the other three: it depends
  on the wordlist actually being reviewed carefully, not a structural/regulatory reserved space.
  Documented as such.
- Neutral: breaking change to golden outputs touching `lastName()`/`fullName()`/`streetAddress()` -
  regenerated and re-frozen as part of this decision, not silently.
- Neutral: `surnames.txt`/`street-themes.txt` no longer carry a third-party (OGL) attribution - they
  fall under this project's own MIT licence as original authored content instead. `street-types.txt`
  is unaffected (still real, still OGL via Royal Mail PAF data) and keeps its existing attribution.
- Neutral: the post-v1 "fictional locale" idea shrinks in scope - it no longer needs to cover
  surnames/streets specifically, since any real locale now provides that. It would still be the only
  way to get every category (including first names, towns, organisation names) fictional at once, if
  that's ever wanted.
