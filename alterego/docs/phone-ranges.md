# Phone-range source provenance

Sibling document to `docs/dictionaries.md`, for `phoneNumber()`'s fictional-range data. Kept
separate because this is a single small "structural rule table" sourced from one regulatory
guidance page, not a dictionary of curated words with its own population-sizing/tokenisation
decisions — the shape doesn't fit `dictionaries.md` naturally.

The same three-part gate as every dictionary file applies (see `docs/dictionaries.md`):
provenance, licensing, data processing. All three are recorded below and in
`src/main/resources/dictionaries/GB/phone-ranges.txt`'s own header.

## Source

**Ofcom**, "Telephone numbers for use in TV and radio drama programmes."
Data: https://www.ofcom.org.uk/phones-and-broadband/phone-numbers/numbers-for-drama
Published: 2019-03-01. Last updated: 2023-05-11. Retrieved: 2026-07-25.

Ofcom's live page returns an anti-bot 403 to automated fetches, and this environment disallows
falling back to a web archive snapshot, so the page content was independently retrieved by the
project owner directly in a browser and pasted verbatim into the implementing session, then
cross-checked beforehand against two independent secondary sources (Wikipedia's "Fictitious
telephone number" article and a third-party open-source generator's data table) — those two
disagreed on the Northern Ireland range specifically (`9018` vs `9649`); the primary source
pasted here confirms `9649` is correct, resolving the discrepancy.

## Licensing — documented exception

Ofcom's own site is under a bespoke Ofcom copyright notice, not one of AlterEgo's three
ordinarily-preferred licences (OGL, MIT, CC0 — `docs/dictionaries.md`'s sourcing policy): free
reproduction, provided it is accurate and acknowledged as Ofcom copyright with the document title
specified. Full text committed at `src/main/resources/dictionaries/LICENCES/Ofcom-copyright.txt`.

Per the sourcing policy, a non-standard licence is used only when no acceptable alternative
exists and is flagged for an independent decision. Both hold here: Ofcom is the sole authority
publishing these reserved ranges (there is no OGL/MIT/CC0 alternative to prefer instead), and its
terms are fully compatible with the `NOTICE`-file attribution mechanism already in place for
other sources. Confirmed as an accepted exception (project owner, 2026-07-25).

## Data processing

Every one of Ofcom's 20 published ranges reduces to the same shape: an 8-digit fixed prefix
(area code, or non-geographic prefix, plus the fixed part of the local number) followed by
exactly 3 freely-varying trailing digits (`000`–`999`, 1000 numbers per range, matching Ofcom's
own "1000 numbers within each range" statement) — no exceptions, verified by hand against every
row. `src/main/resources/dictionaries/GB/phone-ranges.txt` stores each range as `<8-digit
value>\t<display template>`, where the template is Ofcom's own digit-grouping for that range with
`XXX` marking the 3 variable digits (e.g. `0113 496 0XXX`); a build-time check
(`DictionaryWellFormedness.validatePhoneRangeTags`) verifies the template's digits, with spaces
and `XXX` stripped, reconstruct the value exactly.

**Full table as published** (all 20 ranges; see the next section for which are shipped):

| Geographic area                | Area code | Range               |
|---------------------------------|-----------|----------------------|
| Leeds                           | 0113      | 496 0000–496 0999    |
| Sheffield                       | 0114      | 496 0000–496 0999    |
| Nottingham                      | 0115      | 496 0000–496 0999    |
| Leicester                       | 0116      | 496 0000–496 0999    |
| Bristol                         | 0117      | 496 0000–496 0999    |
| Reading                         | 0118      | 496 0000–496 0999    |
| Birmingham                      | 0121      | 496 0000–496 0999    |
| Edinburgh                       | 0131      | 496 0000–496 0999    |
| Glasgow                         | 0141      | 496 0000–496 0999    |
| Liverpool                       | 0151      | 496 0000–496 0999    |
| Manchester                      | 0161      | 496 0000–496 0999    |
| London                          | 020       | 7946 0000–7946 0999  |
| Tyneside/Durham/Sunderland      | 0191      | 498 0000–498 0999    |
| Northern Ireland                | 028       | 9649 6000–9649 6999  |
| Cardiff                         | 029       | 2018 0000–2018 0999  |
| No area (generic fallback)      | 01632     | 960000–960999        |
| Mobile                          | —         | 07700 900000–900999  |
| Freephone                       | —         | 08081 570000–570999  |
| Premium rate services           | —         | 0909 8790000–8790999 |
| UK-wide                         | —         | 03069 990000–990999  |

## Record coherence: area tagging

Each shipped row carries a second tag, for `phoneNumber()`'s section 6.3 coherence with
`city()`/`postcode()`: the associated postcode area (cross-checked
against `dictionaries/GB/towns.txt`'s own area strings), `NONE` for the 01632 no-area range (the
designated geography-neutral fallback when a fixed area matches no range), or `MOBILE` for the
mobile range (never a coherence match target or the neutral fallback — reachable only via the
unconstrained pool, same as before M5). London's single range gets one row per postcode area it
spans (E, EC, N, NW, SE, SW, W, WC), exactly as `towns.txt` already lists London itself — same
"list once per area" precedent, same well-formedness rule (not a duplicate row, since the tags
differ per row). Reading's range (`RG`) has no matching town in `towns.txt`'s curated 20 towns —
documented, not a bug: still reachable via an explicit `.with(UK_POSTCODE_AREA, "RG")` pre-seed.

The unconstrained ("no area fixed") pool must stay exactly the same 17 distinct choices it was in
M3, not 24 rows — `PhoneNumberStrategy` de-duplicates by template before picking, so London's
8-way tag expansion doesn't shift the pick bound and silently change M3's golden outputs.

## v1 scope: shipped pool excludes freephone/premium-rate/UK-wide

**Decided (project owner, 2026-07-25)**: `phoneNumber()`'s default pool draws from only the 17
geographic + no-area + mobile ranges (everything above except the last three rows). Freephone,
premium-rate, and UK-wide numbers are real published Ofcom ranges but don't read as a realistic
personal contact number (section 1's "realistic output" goal) — an ordinary person's phone field
is essentially never one of those number types. This also matches the spec's own section 4.1
illustrative examples (`020 7946 0xxx`, `07700 900xxx`, `01632 960xxx`), which mention only
geographic/mobile/generic, never a special-service range. The three excluded ranges are recorded
above in full so a future version can add them behind an option without re-doing this sourcing
work.
