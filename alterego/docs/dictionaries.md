# Dictionary source provenance

Master index of external data sources used to build AlterEgo's dictionaries. Record a
decision here as soon as it is made, not just once M2 begins. The same information is repeated
as a header comment in the dictionary file(s) that draw on each source (`SPECIFICATION.md`
section 9), and each licence's full text is committed once under `dictionaries/LICENCES/` when
the first dictionary file citing it is added.

**Every dictionary file needs all three of the following — no exceptions, and not satisfied by
having each piece present somewhere else in the project:**

1. **Provenance** — organisation/dataset name, exact original data URL, retrieval date.
2. **Licensing** — a checked, cited licence with committed full text, not an assumption. This
   applies even to plain word lists with no copyright concern (e.g. composed street-name
   vocabulary): the reason there is accuracy and reproducibility, not redistribution rights —
   a word typed from memory can be subtly wrong (spelling, meaning, usage) with no way to catch
   it, so it still needs a real citable source.
3. **Data processing** — the exact reduction/curation rule stated and scripted
   (`tools/curate_dictionaries.py`, matching `tools/verify_vectors.py`'s standing), not manual
   transcription from a report into a file.

See `docs/tasks/M2.md` step 2 for the full gate this maps to.

## Sourcing policy

- **Clean, directly acceptable licences**: Open Government Licence (OGL), MIT, CC0 — each a
  specific, well-defined written legal instrument, not just an assertion.
- **Public domain is not automatically the same tier.** A bare "public domain" claim is a
  statement about copyright status, not a licence, and can be legally shaky depending on
  jurisdiction — CC0 exists specifically because unilateral public-domain dedications don't
  work reliably worldwide. Genuine public domain via *expired copyright* (e.g. old census data
  where the term has actually run out) is solid and acceptable; an *asserted* dedication with no
  CC0 or equivalent instrument behind it is flagged for a human decision, same as the informal
  "no restrictions, free to use" cases below.
- Licences requiring share-alike on the derived work (e.g. ODbL, CC-BY-SA) or restricting
  redistribution (view-only, non-commercial-only, accredited-researcher-only), and any other
  non-standard or informally-stated position (e.g. Companies House's Free Company Data Product)
  are avoided unless no acceptable alternative exists, and any use of one is flagged explicitly
  for a human decision before committing data under it.
- **Strong preference for UK-government-associated sources** (ONS, Ordnance Survey, National
  Records of Scotland, NISRA, Companies House, data.gov.uk) over academic, community, or
  commercial sources, given their consistent OGL licensing and official standing — even when a
  non-government source would offer broader coverage.
- Every entry below must record: organisation and dataset name, the exact original data URL, the
  licence name and its exact original URL, and the retrieval date (spec section 9).

## Attribution placement

OGL requires acknowledgement of the source whenever the information is copied, published,
distributed, or adapted — including by anyone who redistributes AlterEgo itself, since the JAR
transitively bundles this data into every application that depends on it. A per-file provenance
header alone is too easy to miss, so every attribution string recorded in this document must
also appear:

1. In the dictionary file's own provenance header (per-file, primary record).
2. In a root `NOTICE` file consolidating every source's exact required attribution string.
3. Packaged into `META-INF/` inside the built JAR — both `NOTICE` and the root `LICENCE` file
   (MIT; covers the source code only, not the OGL data — see its own text) are copied there by
   a Gradle task on `jar`, not automatic. Most consumers receive only the JAR, never the
   repository, so both files must travel inside the artifact itself.
4. Referenced from `README.md`, stating plainly that dictionary data derives from
   OGL-licensed UK government sources and pointing at `NOTICE`, so downstream users know their
   own OGL obligation exists and where to find the text rather than discovering it themselves.

This is done: `LICENCE` and `NOTICE` exist at the repo root and are packaged into
`META-INF/LICENCE` and `META-INF/NOTICE` in the built JAR (verified: `unzip -l` on a built jar
shows both). `NOTICE` already reflects every source decided so far in this document; update it
if a sourcing decision below changes.

## Surnames

**Status: decided for v1 — all four UK nations, each sized to its real population share rather
than treated as equal-sized or as one default nation plus adjustments to it.**

**Size every nation's contribution to its real population share, calculated the
same way for all four nations rather than picking one as the default.** England & Wales, at
89.2% of the UK population, draws on two ONS cohort years (2024 and 2014, see below); Scotland,
at 8.0%, draws on NRS's top 10 for a single current year. An exactly proportional count would
give Scotland roughly 2 entries in a pool this size — nowhere near enough to be a genuine,
recognisable representation of Scottish naming — so top 10 was chosen deliberately, as a level
big enough to actually represent Scottish surnames rather than token them, without creating
a distortion in the other direction. Northern Ireland has no equivalent surname
publication to draw from at all (confirmed via ONS/NRS FOI history), and Wales has no separate
figure of its own to draw from either — ONS publishes "England and Wales" as one combined
ranking, not two national ones. Both are gaps in what the UK's own statistics bodies currently
publish, not a choice this project made; worth revisiting the moment either gap is filled by a
new official source.

- **England and Wales**: "Twenty most common surnames for births in England and Wales, 2014 and
  2024" — Office for National Statistics. Both years used (see cohort-blending note below).
  Data: https://www.ons.gov.uk/aboutus/transparencyandgovernance/freedomofinformationfoi/twentymostcommonsurnamesforbirthsinenglandandwales2014and2024
  Licence: Open Government Licence v3.0 — http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
  Retrieved: 2026-07-14

- **Scotland**: "Most common surnames, 2025" — National Records of Scotland. Top 10 (see the
  population-sizing note above), single current year (no older cohort for Scotland — see the
  "First names" cohort-blending note below for why an older Scotland cohort wasn't chased).
  Data: https://www.nrscotland.gov.uk/publications/most-common-surnames-2025/
  Licence: Open Government Licence v3.0 (Crown Copyright) — http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
  Retrieved: 2026-07-14

**Combination rule**: 2024 top-20 + 2014 top-20 + Scotland top-10, deduplicated, gives **28
unique surnames**.

## First names

**Status: decided for v1 — all four UK nations, each sized to its real population share, same
principle as surnames.**

**As with surnames — size each nation's contribution to its population
share**: England & Wales draws on two cohort years 29 years apart, to cover more than one
generation; Scotland and Northern Ireland each draw their top 10 boys' + top 10 girls' names for
the current year.

- **England and Wales**: "Baby names in England and Wales, 1996 to 2025" — Office for National
  Statistics. Top 20 boys' + top 20 girls' names for both 2025 (the most recent year published)
  and 1996 (the first year this ONS series covers) used.
  Data: https://www.ons.gov.uk/peoplepopulationandcommunity/birthsdeathsandmarriages/livebirths/datasets/babynamesinenglandandwalesfrom1996
  Licence: Open Government Licence v3.0 — http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
  Retrieved: 2026-07-14

- **Scotland**: "Babies' First Names, 2025" — National Records of Scotland. Top 10 boys' + top
  10 girls' names, single current year only.
  Data: https://www.nrscotland.gov.uk/publications/babies-first-names-2025/
  Licence: Open Government Licence v3.0 — http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
  Retrieved: 2026-07-14

- **Northern Ireland**: "Top 10 Baby Names, 1997 to 2025" — Northern Ireland Statistics and
  Research Agency (NISRA). 2025 column only. This specific table (rather than NISRA's raw
  per-district dashboard export) was used because it's a clean, already-computed national top 10
  — the raw dashboard file's per-district pivot didn't produce a sane national ranking when
  queried directly, and re-deriving one from it wasn't worth chasing for a "close enough" target.
  Data: https://www.nisra.gov.uk/publications/baby-names-2025
  Licence: Open Government Licence v3.0, confirmed via NISRA's own Crown Copyright page
  (https://www.nisra.gov.uk/crown-copyright).
  Retrieved: 2026-07-14

**Deduplication detail**: 2025+1996 England & Wales (80 raw) + Scotland top-10×2 (20 raw) +
Northern Ireland top-10×2 (20 raw) = 120 raw entries, reducing to **89 unique first names**.
"Muhammad"/"Mohammed" and "Finley"/"Finlay" are different spellings, each independently ranked
in their own source; kept distinct, same reasoning as Thompson/Thomson in surnames.

The final lists, and the script that produces them deterministically from these sources, are
tracked against `tools/curate_dictionaries.py` (spec section 9's "repeatable, documented
process" requirement — see `docs/tasks/M2.md` step 2).

## Towns/cities + postcode area + nation tags

**Status: decided for v1.** A curated top-20 UK-wide (England + Wales + Scotland + Northern
Ireland, since ISO "GB" covers the whole UK) population ranking: postcode areas
for major cities are stable, publicly documented facts, verifiable without a bulk dataset.

Sourcing is less uniform than the other categories and this is recorded honestly: England's
figures are ONS 2021 Census built-up area populations (via Wikipedia's sourced "List of ONS
built-up areas in England by population"); Wales is 2021 Census local-authority figures;
Scotland is National Records of Scotland city-population estimates (not all clearly
2021-Census-dated); Northern Ireland is NISRA-sourced Belfast 2021 Census figures via secondary
aggregation. Postcode areas cross-checked against Royal Mail-derived reference sources (e.g.
Wikipedia's "List of postcode areas in the United Kingdom").

The resulting ranking is a strict UK-wide population order, not a per-nation quota — composition
is England 16 / Scotland 2 / Wales 1 / Northern Ireland 1 (Swansea, Newport, Aberdeen, Dundee,
Derry, and Lisburn all fall just below the cutoff).

**Judgement call, decided**: London spans 8 postcode areas (E, EC, N, NW, SE, SW, W, WC), not
one, so it doesn't fit the dictionary's one-area-per-entry model as a single row. Decision:
**list London once per major postcode area** (8 rows, all tagged `ENGLAND`), rather than picking
one inaccurate representative area or excluding the UK's largest city entirely.

The 20-town, 27-row (with London's 8 areas) list, and the script that produces it, are tracked
against `tools/curate_dictionaries.py` alongside the first-names curation (see above).

## Street names

**Status: decided for v1 — Compositional (theme word + type word).**

**Decision**: two small composed word lists — `street-themes.txt` (descriptive words)
and `street-types.txt` (road-type words: "Road", "Avenue", "Close"...), combined at generation
time (e.g. "Victoria" + "Road" → "Victoria Road"). Both flat lists.

Street-type words (25): Avenue, Close, Court, Crescent, Drive, Gardens, Gate, Green,
Grove, Hill, Lane, Mews, Mount, Orchard, Place, Plaza, Road, Row, Square, Street, Terrace, Vale,
View, Walk, Way.
Source: Ideal Postcodes, "UK PAF Thoroughfare Descriptors" — a third-party compilation of Royal
Mail's PAF descriptor list.
Data: https://ideal-postcodes.co.uk/guides/thoroughfare-descriptors
Licence: not stated on the page — a third-party mirror, not Royal Mail's own page directly.
Corroborated via Royal Mail's own official "Programmers Guide – Technical specifications for
users of PAF" (poweredbypaf.com/resources/), which independently confirms PAF holds
"approximately 200 Descriptor words" as a standard list. Treated as accuracy-only sourcing
(ordinary English words, not creative or copyrightable content in their own right).
Retrieved: 2026-07-14

Street theme words (25): Albert, Alexander, Broadway, Chester, Church, George, Grange, High,
Highfield, King, London, Main, Manor, Mill, New, Park, Queen, Richmond, School, Springfield,
Stanley, Station, Victoria, Windsor, York.
Source: drivers.co.uk, "Revealed: The UK's most common street names", and thefactsite.com, "The
Most Common Street Names In The UK" — both secondary reporting, ultimately over OS Open Names
frequency data (OGL v3.0), not a primary government publication.
Data: https://drivers.co.uk/general/revealed-the-uks-most-common-street-names/;
https://www.thefactsite.com/most-common-street-names-in-uk/
Licence: not stated on either page directly; treated as accuracy-only sourcing (ordinary English
words, not creative or copyrightable content in their own right), same basis as the street-type
words above.
Words already used as street *types* (Green, Grove, Avenue, Crescent, Drive) are deliberately
excluded here to avoid degenerate combinations like "Green Green".
Retrieved: 2026-07-14

## Organisation-name components

**Status: decided and curated for v1** — UK IPO Trade Mark Data Release, Domestic UK
Applications dataset, mined for common word tokens (never redistributed as real company/owner
names).

- **UK IPO Trade Mark Data Release** — Intellectual Property Office. Domestic UK Applications
  dataset, "Name" field (real trade mark applicant/owner names, many of them companies).
  Data: https://www.gov.uk/government/publications/ipo-trade-mark-data-release
  Licence: Open Government Licence v3.0, confirmed directly on the primary gov.uk page ("All
  content is available under the Open Government Licence v3.0") —
  http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
  Retrieved (licence confirmed): 2026-07-14

**Method** (fully scripted, `tools/mine_org_components.py`): the dataset is a single 817 MB
pipe-delimited UTF-16 file inside a 60 MB ZIP.
Rows are filtered to `Country == "United Kingdom"`, then deduplicated by owner name (a single
owner can file many trade marks under the same name — counting every row would let one prolific
filer's name dominate token frequency). Each of the 346,617 distinct UK owner names is tokenised
into words; legal-form words (Limited, Ltd, Plc, LLP, Cyf, Ccc...), ordinary English stopwords,
and honorific/domain noise (Mr, Mrs, Com...) are dropped. The raw ZIP itself isn't kept in this
repository — it's a stable, versioned direct download (last modified 2018), so re-running the
script against a fresh copy reproduces the same output; only the small derived candidate list is
kept, at `tools/data-cache/org-components-candidates.tsv` (133,424 tokens after filtering).

**Personal-name filtering, a real finding not a guess**: many UK trade mark owners are sole
traders filing under their own name, so the raw frequency table was dominated by personal names
(John, David, Smith, Mrs...) ahead of any genuine organisational vocabulary. Rather than
hand-type a stoplist, the filter is built from data already cached in this repo for other
dictionaries: every first name appearing anywhere in the ONS 1996–2025 baby names series
(39,477 names — not just the curated top-20s used for `first-names.txt`) and every surname in
NRS's "Surnames TimeSeries 1975 to 2025" (3,672 surnames) — both real, sourced, already-cited
data, not invented. This removed 14,119 tokens from the candidate list.

**Generation algorithm, decided**: a flat single pool risks nonsensical composition.
Resolved by tagging every entry `MODIFIER` or `NOUN`
(`organisation-components.txt` is a tagged dictionary, same file-format mechanism as towns'
postcode-area/nation tags — see `DictionaryWellFormedness.validateOrgComponentTags`) and
generating a name as three distinct words: `[MODIFIER-or-NOUN] + NOUN + NOUN`. Position 1 may be
either category; positions 2 and 3 must be `NOUN` and distinct from every word already chosen.
This permits both real patterns ("Northern Trading Solutions", MODIFIER+NOUN+NOUN; "Trading
Solutions Partners", NOUN+NOUN+NOUN) while making same-word repeats and MODIFIER+MODIFIER
pairings structurally impossible, not just unlikely.

Three words rather than two specifically to keep the combination count high: unlike
`streetAddress()` (house number + street name gives extra differentiation) or personal names
(real-world collisions are normal — many people share a name), two different real organisations
landing on the same pseudonymised name would be a much more visible artefact, and organisation
names are rarely duplicated in reality. With **31 `MODIFIER` + 44 `NOUN`** entries (75 total),
the combination count is `44 × 43 × 73 = 138,116` — comfortably past a 50,000-combination floor
without needing to bloat the dictionary the way a two-word design would have.

**NOUN (44 entries)**: the original top-50-by-frequency cut from the candidate list, minus "Sons"
(dropped: it grammatically wants a personal surname before it, e.g. "Smith & Sons", which this
composition scheme doesn't supply), minus same-root/near-synonym exclusions, and
minus the six words later reclassified as `MODIFIER` (which were never in this bucket to begin
with). Group, Company, International, Services, Solutions, Trading, Systems, Holdings, Products,
Management, Partnership, Media, Design, Foods, Technology, Trust, Business, Marketing,
Consulting, Association, Global, Associates, Europe, Engineering, Health, Enterprises, Club,
Clothing, Communications, Direct, Software, Partners, House, Centre, Care, Training, Productions,
Sports, Capital, Property, Leisure, World, Supplies, Medical.

**MODIFIER (31 entries)**: place/region names and demonym/scope adjectives, all verified present
in the mined candidate list (not invented) — deliberately added beyond raw top-N frequency, since
place words rank well below generic corporate vocabulary on frequency alone (e.g. "Northern"
reaches only rank ~160) and wouldn't appear in a pure top-50 cut. Northern, Southern, Eastern,
British, Scottish, Irish, Cornish, Cymru, Oxford, Manchester, Yorkshire, Bristol, Edinburgh,
Leeds, Birmingham, Sheffield, Liverpool, Leicester, Newcastle, Cardiff, Belfast, Midlands,
Highland, Sussex, Essex, Surrey, Worldwide, National, European, Central, Imperial.

**Judgement calls, decided**: a specific institutional abbreviation ("NHS", rank ~218) was
excluded as too specific to be generic vocabulary, not because of any licensing concern. Generic
filler adjectives seen in the candidate list (Your, All, Just, Big, Pro, Plus) fell below the
top-50 cutoff and were left out on frequency grounds alone, without needing a separate judgement
call.
