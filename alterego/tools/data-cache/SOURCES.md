# Cached raw source spreadsheets

Raw downloads backing `tools/curate_dictionaries.py`'s `SURNAMES`/`FIRST_NAMES` sections, cached
here so re-running the curation script (or picking the data apart differently later) doesn't
depend on these pages staying up or unchanged. All OGL v3.0 (Open Government Licence) - see
`docs/research/0001-alterego-dictionaries.md` for the full per-category provenance and
`dictionaries/LICENCES/OGL-v3.txt` for the licence text.

| File | Source | Data URL | Retrieved |
| --- | --- | --- | --- |
| `ons-surnames-2014-2024.xlsx` | ONS: Twenty most common surnames for births in England and Wales, 2014 and 2024 | <https://www.ons.gov.uk/aboutus/transparencyandgovernance/freedomofinformationfoi/twentymostcommonsurnamesforbirthsinenglandandwales2014and2024> | 2026-07-14 |
| `ons-babynames-1996-2025.xlsx` | ONS: Baby names in England and Wales, 1996 to 2025 | <https://www.ons.gov.uk/peoplepopulationandcommunity/birthsdeathsandmarriages/livebirths/datasets/babynamesinenglandandwalesfrom1996> | 2026-07-14 |
| `nrs-surnames-2025.xlsx` | National Records of Scotland: Most common surnames, 2025 (includes the 1975-2025 time series) | <https://www.nrscotland.gov.uk/publications/most-common-surnames-2025/> | 2026-07-14 |
| `nrs-babies-first-names-2025-summary.xlsx` | National Records of Scotland: Babies' First Names, 2025 (summary tables) | <https://www.nrscotland.gov.uk/publications/babies-first-names-2025/> | 2026-07-14 |
| `nisra-baby-names-dashboard-data-2025.xlsx` | NISRA: Data for Baby Names Dashboard 2025 | <https://www.nisra.gov.uk/publications/data-baby-names-dashboard> | 2026-07-14 |

These are the raw files as published - `tools/curate_dictionaries.py` extracts specific
ranks/years from them by hand-copied values (see the script's own source comments for exactly
which rows). They are not read programmatically by the script itself.

Known limitation, already investigated and not worth re-chasing: see
`docs/research/0001-alterego-dictionaries.md`, "First names", for why Scotland and Northern
Ireland are single-cohort while England & Wales gets two.
