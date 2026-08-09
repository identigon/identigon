#!/usr/bin/env python3
"""Generates AlterEgo's dictionary files under src/main/resources/dictionaries/GB/ from
verified source data. This is the repeatable, documented process required by SPECIFICATION.md
section 9 and docs/dictionaries.md's three-part gate: every value below is cited to a specific
source recorded in docs/dictionaries.md, not typed from memory. Re-running this script
reproduces the same committed files byte-for-byte.

Multi-source files (surnames, first names, towns) record every source in one semicolon-joined
"source"/"data-url" header line each, rather than extending the dictionary file format to a
repeated-key structure — see docs/dictionaries.md for the full per-source breakdown this
summarises.

Run: python3 tools/curate_dictionaries.py
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT_DIR = ROOT / "src" / "main" / "resources" / "dictionaries" / "GB"
RETRIEVED = "2026-07-14"
LICENCE = "OGL-v3"
LICENCE_URL = "http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/"


def header(source: str, data_url: str) -> str:
    lines = [
        f"# source: {source}",
        f"# data-url: {data_url}",
        f"# licence: {LICENCE}",
        f"# licence-url: {LICENCE_URL}",
        f"# retrieved: {RETRIEVED}",
    ]
    return "\n".join(lines) + "\n"


def write_flat(name: str, source: str, data_url: str, values: list[str]) -> None:
    deduped_sorted = sorted(set(values))
    content = header(source, data_url) + "\n".join(deduped_sorted) + "\n"
    path = OUTPUT_DIR / f"{name}.txt"
    path.write_text(content, encoding="utf-8")
    print(f"Wrote {path} ({len(deduped_sorted)} entries)")


def write_tagged(name: str, source: str, data_url: str, rows: list[tuple[str, ...]]) -> None:
    # Sorted by value only (Appendix well-formedness rule); rows sharing a value (e.g. London's
    # several postcode areas) keep their relative input order, since Python's sort is stable.
    rows_sorted = sorted(rows, key=lambda r: r[0])
    lines = ["\t".join(row) for row in rows_sorted]
    content = header(source, data_url) + "\n".join(lines) + "\n"
    path = OUTPUT_DIR / f"{name}.txt"
    path.write_text(content, encoding="utf-8")
    print(f"Wrote {path} ({len(rows_sorted)} rows)")


# --- Surnames --------------------------------------------------------------------------------
# Authored, not sourced (ADR 0010, docs/dictionaries.md "Surnames"): deliberately obviously
# fictional surnames, not generated from any real dataset. src/main/resources/dictionaries/GB/
# surnames.txt is maintained directly, not by this script.

# --- First names -------------------------------------------------------------------------------
# Same reasoning as surnames above: England & Wales (ONS) is the size anchor, with two cohort
# years 29 years apart (2025, the most recent year published, and 1996, the first year the ONS
# series covers) so the pool isn't entirely current-decade baby names. Scotland (NRS) and
# Northern Ireland (NISRA) each contribute their top 10 boys' + top 10 girls' names for a single
# current year only (2025) — not the full top 20, and not blended across cohort years the way
# England & Wales is (NRS's own historical-rank data only tracks names still in today's
# top 100, so it silently drops names like Ryan or Scott that were popular in the 1990s but have
# since fallen out — see tools/data-cache/SOURCES.md for
# why that source wasn't used). "Muhammad"/"Mohammed" and "Finley"/"Finlay" are different
# spellings, each independently ranked in their own source; kept distinct.

ONS_GIRLS_2025 = [
    "Olivia", "Lily", "Amelia", "Isla", "Florence", "Freya", "Poppy", "Elsie", "Ivy", "Isabella",
    "Ava", "Evelyn", "Sophia", "Phoebe", "Sienna", "Mabel", "Sofia", "Daisy", "Matilda", "Willow",
]
ONS_BOYS_2025 = [
    "Muhammad", "Noah", "Leo", "Luca", "Arthur", "Oliver", "George", "Oscar", "Theodore",
    "Freddie", "Archie", "Theo", "Henry", "Jude", "Arlo", "Alfie", "Rory", "Finley", "Harry",
    "Mohammed",
]
ONS_GIRLS_1996 = [
    "Sophie", "Chloe", "Jessica", "Emily", "Lauren", "Hannah", "Charlotte", "Rebecca", "Amy",
    "Megan", "Shannon", "Katie", "Bethany", "Emma", "Lucy", "Georgia", "Laura", "Sarah", "Jade",
    "Danielle",
]
ONS_BOYS_1996 = [
    "Jack", "Daniel", "Thomas", "James", "Joshua", "Matthew", "Ryan", "Joseph", "Samuel", "Liam",
    "Jordan", "Luke", "Connor", "Alexander", "Benjamin", "Adam", "Harry", "Jake", "George",
    "Callum",
]
NRS_GIRLS_2025_TOP10 = [
    "Freya", "Isla", "Olivia", "Amelia", "Grace", "Emily", "Millie", "Lily", "Sophia", "Rosie",
]
NRS_BOYS_2025_TOP10 = [
    "Noah", "Luca", "Rory", "Muhammad", "Oliver", "Theo", "Leo", "Archie", "Finlay", "Harris",
]
NISRA_GIRLS_2025_TOP10 = [
    "Grace", "Fiadh", "Olivia", "Isla", "Lily", "Emily", "Annie", "Aoife", "Meabh", "Freya",
]
NISRA_BOYS_2025_TOP10 = [
    # Rank 9 was a tie (Arthur / Jude); rank 10 was suppressed/blank in the source table, so this
    # is 9 ranks but 10 names.
    "Noah", "Jack", "James", "Charlie", "Leo", "Oisin", "Theo", "Luca", "Arthur", "Jude",
]

FIRST_NAMES_SOURCE = (
    "Office for National Statistics: Baby names in England and Wales, 1996 to 2025 (top 20 "
    "boys' and girls' names for 2025 and 1996 both used); "
    "National Records of Scotland: Babies' First Names, 2025 (top 10 only); "
    "Northern Ireland Statistics and Research Agency: Top 10 Baby Names, 1997 to 2025 (2025 "
    "column only)"
)
FIRST_NAMES_DATA_URL = (
    "https://www.ons.gov.uk/peoplepopulationandcommunity/birthsdeathsandmarriages/livebirths/"
    "datasets/babynamesinenglandandwalesfrom1996; "
    "https://www.nrscotland.gov.uk/publications/babies-first-names-2025/; "
    "https://www.nisra.gov.uk/publications/baby-names-2025"
)

# --- Towns/cities + postcode area + nation -------------------------------------------------
# Curated top-20 UK-wide population ranking (all four nations). London spans 8 postcode areas
# and is listed once per area (see docs/dictionaries.md's well-formedness exception for this).

TOWNS = [
    ("London", "E", "ENGLAND"),
    ("London", "EC", "ENGLAND"),
    ("London", "N", "ENGLAND"),
    ("London", "NW", "ENGLAND"),
    ("London", "SE", "ENGLAND"),
    ("London", "SW", "ENGLAND"),
    ("London", "W", "ENGLAND"),
    ("London", "WC", "ENGLAND"),
    ("Birmingham", "B", "ENGLAND"),
    ("Glasgow", "G", "SCOTLAND"),
    ("Leeds", "LS", "ENGLAND"),
    ("Edinburgh", "EH", "SCOTLAND"),
    ("Liverpool", "L", "ENGLAND"),
    ("Sheffield", "S", "ENGLAND"),
    ("Manchester", "M", "ENGLAND"),
    ("Bristol", "BS", "ENGLAND"),
    ("Leicester", "LE", "ENGLAND"),
    ("Cardiff", "CF", "WALES"),
    ("Belfast", "BT", "NORTHERN_IRELAND"),
    ("Coventry", "CV", "ENGLAND"),
    ("Bradford", "BD", "ENGLAND"),
    ("Nottingham", "NG", "ENGLAND"),
    ("Newcastle upon Tyne", "NE", "ENGLAND"),
    ("Brighton and Hove", "BN", "ENGLAND"),
    ("Derby", "DE", "ENGLAND"),
    ("Kingston upon Hull", "HU", "ENGLAND"),
    ("Plymouth", "PL", "ENGLAND"),
]
TOWNS_SOURCE = (
    "ONS 2021 Census built-up area populations (England); 2021 Census local-authority figures "
    "(Wales); National Records of Scotland city-population estimates (Scotland); NISRA-sourced "
    "2021 Census figures (Northern Ireland); postcode areas cross-checked against Royal "
    "Mail-derived reference sources. See docs/dictionaries.md for the full per-nation "
    "source breakdown."
)
TOWNS_DATA_URL = (
    "https://en.wikipedia.org/wiki/List_of_ONS_built-up_areas_in_England_by_population "
    "(and equivalent per-nation sources cited in docs/dictionaries.md)"
)

# --- Street themes -----------------------------------------------------------------------------
# Authored, not sourced (ADR 0010, docs/dictionaries.md "Street names"): deliberately obviously
# fictional theme words, not generated from real UK street-name frequency data.
# src/main/resources/dictionaries/GB/street-themes.txt is maintained directly, not by this script.

# --- Street types --------------------------------------------------------------------------------
# Royal Mail PAF thoroughfare descriptors, corroborated against Royal Mail's own Programmers Guide.
# A flat list, not tagged.

STREET_TYPES = [
    "Avenue", "Close", "Court", "Crescent", "Drive", "Gardens", "Gate", "Green", "Grove",
    "Hill", "Lane", "Mews", "Mount", "Orchard", "Place", "Plaza", "Road", "Row", "Square",
    "Street", "Terrace", "Vale", "View", "Walk", "Way",
]
STREET_TYPES_SOURCE = (
    "Ideal Postcodes 'UK PAF Thoroughfare Descriptors' guide, corroborated against Royal Mail's "
    "own PAF Programmers Guide"
)
STREET_TYPES_DATA_URL = "https://ideal-postcodes.co.uk/guides/thoroughfare-descriptors"

# --- Organisation-name components ---------------------------------------------------------------
# Mined from real UK trade mark owner names (never redistributed as owner names — only the
# resulting word-frequency counts are used). See tools/mine_org_components.py for the full
# mining/filtering method and tools/data-cache/org-components-candidates.tsv for the complete
# 133,424-token candidate list this is drawn from.
#
# Tagged MODIFIER/NOUN (docs/dictionaries.md, "Organisation-name components", has the full
# generation-algorithm reasoning): a name composes as [MODIFIER-or-NOUN] + NOUN + NOUN, each word
# distinct, so two MODIFIER-flavoured words (both place-like) can never land next to each other
# ("Yorkshire Manchester") and the same word can never repeat. "Sons" was dropped — it wants a
# personal surname before it ("Smith & Sons"), not a generic modifier or noun, which this scheme
# doesn't supply.
#
# NOUN is the original top-50-by-frequency cut, minus Sons and minus one word from each of four
# same-root/near-synonym pairs that would otherwise read redundantly if drawn together in one
# name (Technology/Technologies, Food/Foods, Partner/Partners, Consulting/Consultancy) — 44
# words. The distinct-word generation rule only prevents the exact same string twice, not two
# different spellings of the same root, so these had to be resolved by hand. MODIFIER is 30
# words (place/region names and demonym/scope adjectives) added deliberately, since these rank
# far below generic corporate vocabulary by raw frequency alone and wouldn't appear in a pure
# top-N cut. 31 MODIFIER x 44 NOUN gives 44 x 43 x 73 = 138,116 three-word combinations.

ORG_MODIFIERS = [
    "Northern", "Southern", "Eastern", "British", "Scottish", "Irish", "Cornish", "Cymru",
    "Oxford", "Manchester", "Yorkshire", "Bristol", "Edinburgh", "Leeds", "Birmingham",
    "Sheffield", "Liverpool", "Leicester", "Newcastle", "Cardiff", "Belfast", "Midlands",
    "Highland", "Sussex", "Essex", "Surrey", "Worldwide", "National", "European", "Central",
    "Imperial",
]
ORG_NOUNS = [
    "Group", "Company", "International", "Services", "Solutions", "Trading", "Systems",
    "Holdings", "Products", "Management", "Partnership", "Media", "Design", "Foods",
    "Technology", "Trust", "Business", "Marketing", "Consulting", "Association", "Global",
    "Associates", "Europe", "Engineering", "Health", "Enterprises", "Club", "Clothing",
    "Communications", "Direct", "Software", "Partners", "House", "Centre", "Care", "Training",
    "Productions", "Sports", "Capital", "Property", "Leisure", "World", "Supplies", "Medical",
]
ORG_COMPONENTS_SOURCE = (
    "UK Intellectual Property Office: Trade Mark Data Release, Domestic UK Applications dataset "
    "— word tokens mined from the 'Name' (owner name) field, filtered against personal names and "
    "legal-form words (tools/mine_org_components.py); no owner names themselves are used or "
    "redistributed"
)
ORG_COMPONENTS_DATA_URL = "https://www.gov.uk/government/publications/ipo-trade-mark-data-release"


def main() -> None:
    # surnames.txt and street-themes.txt are authored, not generated by this script — see the
    # "Surnames"/"Street themes" comments above and docs/dictionaries.md.
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    write_flat(
        "first-names",
        FIRST_NAMES_SOURCE,
        FIRST_NAMES_DATA_URL,
        ONS_GIRLS_2025 + ONS_BOYS_2025 + ONS_GIRLS_1996 + ONS_BOYS_1996
        + NRS_GIRLS_2025_TOP10 + NRS_BOYS_2025_TOP10
        + NISRA_GIRLS_2025_TOP10 + NISRA_BOYS_2025_TOP10,
    )
    write_tagged("towns", TOWNS_SOURCE, TOWNS_DATA_URL, TOWNS)
    write_flat("street-types", STREET_TYPES_SOURCE, STREET_TYPES_DATA_URL, STREET_TYPES)
    write_tagged(
        "organisation-components",
        ORG_COMPONENTS_SOURCE,
        ORG_COMPONENTS_DATA_URL,
        [(word, "MODIFIER") for word in ORG_MODIFIERS] + [(word, "NOUN") for word in ORG_NOUNS],
    )


if __name__ == "__main__":
    main()
