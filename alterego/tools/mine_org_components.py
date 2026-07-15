#!/usr/bin/env python3
"""Mines common word tokens from real trade mark owner names, for AlterEgo's UK
organisation-name-components dictionary (docs/dictionaries.md, "Organisation-name components").

Source: UK IPO Trade Mark Data Release, Domestic UK Applications dataset (OGL v3.0).
https://www.gov.uk/government/publications/ipo-trade-mark-data-release

This never redistributes real owner names — only the resulting word-frequency counts are
written out, and only generic/thematic words (not owner names themselves) are ever promoted
into the shipped dictionary. The raw ZIP (60 MB compressed, ~800 MB decompressed) is not kept in
this repository: it's a stable, versioned direct download (last modified 2018), so re-running
this script against a freshly downloaded copy reproduces the same output.

Method:
1. Stream-decode the pipe-delimited (not tab-delimited, despite gov.uk's own docs saying tab)
   UTF-16 file directly from the ZIP, one row at a time — never write the 800 MB decompressed
   file to disk.
2. Keep only rows where Country == "United Kingdom", so mined vocabulary stays UK-flavoured
   rather than picking up foreign-registrant business terms.
3. Deduplicate by owner Name first (a single owner can file many trade marks under the same
   name — counting every row would let one prolific filer's name dominate token frequency).
4. Tokenise each distinct name into alphabetic words, uppercase for counting.
5. Drop legal-suffix and legal-form words (handled separately by the spec's own suffix
   mechanism, section 4.2, or just legal boilerplate, not thematic vocabulary), common English
   stopwords, and single-character tokens.
6. Drop personal first names and surnames, using a blocklist built from data already cached in
   this repo (tools/data-cache/ons-babynames-1996-2025.xlsx: every first name appearing anywhere
   in the ONS 1996-2025 series, not just the curated top-20s used elsewhere; and
   tools/data-cache/nrs-surnames-2025.xlsx's "Surnames TimeSeries 1975 to 2025" sheet: every
   surname appearing anywhere in that series) — real sourced data, not a hand-typed stoplist.
   Many UK trade mark owners are sole traders filing under their own name, so without this step
   personal names (John, Smith, Mrs...) dominate the raw frequency table.
7. Write every surviving token and its distinct-owner-name frequency to
   tools/data-cache/org-components-candidates.tsv, sorted by frequency descending — a small,
   repeatable, reviewable artifact, not the raw data itself.

Run: python3 tools/mine_org_components.py <path-to-downloaded-opendatadomestic.zip>
"""

import collections
import io
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA_CACHE = ROOT / "tools" / "data-cache"
OUTPUT = DATA_CACHE / "org-components-candidates.tsv"
ONS_BABYNAMES_XLSX = DATA_CACHE / "ons-babynames-1996-2025.xlsx"
NRS_SURNAMES_XLSX = DATA_CACHE / "nrs-surnames-2025.xlsx"

_XLSX_NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
_CELL_REF_RE = re.compile(r"([A-Z]+)(\d+)")


def _shared_strings(z: zipfile.ZipFile) -> list[str]:
    root = ET.fromstring(z.read("xl/sharedStrings.xml"))
    return [
        "".join(t.text or "" for t in si.iter(f"{{{_XLSX_NS['m']}}}t"))
        for si in root.findall("m:si", _XLSX_NS)
    ]


def _column_values(z: zipfile.ZipFile, sheetfile: str, column: str, first_row: int) -> set[str]:
    shared = _shared_strings(z)
    sheet = ET.fromstring(z.read(sheetfile))
    values: set[str] = set()
    for row in sheet.iter(f"{{{_XLSX_NS['m']}}}row"):
        for c in row:
            match = _CELL_REF_RE.match(c.attrib["r"])
            col, rownum = match.group(1), int(match.group(2))
            if col != column or rownum < first_row:
                continue
            t = c.attrib.get("t")
            val_el = c.find("m:v", _XLSX_NS)
            val = val_el.text if val_el is not None else None
            if t == "s" and val is not None:
                val = shared[int(val)]
            if val:
                values.add(val.upper())
    return values


def build_personal_name_blocklist() -> set[str]:
    first_names: set[str] = set()
    z = zipfile.ZipFile(ONS_BABYNAMES_XLSX)
    for sheetfile in ("xl/worksheets/sheet4.xml", "xl/worksheets/sheet5.xml"):  # girls, boys
        first_names |= _column_values(z, sheetfile, "A", first_row=6)

    z2 = zipfile.ZipFile(NRS_SURNAMES_XLSX)
    surnames = _column_values(z2, "xl/worksheets/sheet5.xml", "B", first_row=5)  # TimeSeries

    print(f"personal-name blocklist: {len(first_names)} first names, {len(surnames)} surnames")
    return first_names | surnames

# Legal-form / suffix words: not thematic vocabulary, either handled separately (spec 4.2:
# Ltd, plc, Cyf., c.c.c.) or just legal boilerplate not worth mining as a "component".
LEGAL_FORM_STOPWORDS = {
    "LIMITED", "LTD", "PLC", "LLP", "LP", "INC", "INCORPORATED", "CORP", "CORPORATION", "CIC",
    "CYF", "CCC", "CO",
}

# Ordinary English function words that would otherwise pollute the frequency table.
ENGLISH_STOPWORDS = {
    "AND", "THE", "OF", "FOR", "IN", "TO", "A", "ON", "AT", "BY", "WITH", "UK", "GB", "AN", "IS",
    "AS", "OR", "ITS",
}

# Honorifics (not organisation vocabulary) and web-domain fragments (".com" etc split out by
# the tokeniser, not a real word).
NOISE_STOPWORDS = {
    "MR", "MRS", "MISS", "MS", "DR", "PROF", "SIR", "MADAM", "MADAME", "REV",
    "COM", "WWW", "ORG", "NET",
}

STOPWORDS = LEGAL_FORM_STOPWORDS | ENGLISH_STOPWORDS | NOISE_STOPWORDS

TOKEN_RE = re.compile(r"[A-Za-z]+")


def mine(zip_path: Path) -> collections.Counter:
    z = zipfile.ZipFile(zip_path)
    (inner_name,) = z.namelist()
    token_counts: collections.Counter = collections.Counter()
    seen_owner_names: set[str] = set()
    total_rows = 0
    with z.open(inner_name) as raw:
        f = io.TextIOWrapper(raw, encoding="utf-16", newline="")
        header = f.readline().rstrip("\r\n").split("|")
        name_idx = header.index("Name")
        country_idx = header.index("Country")
        for line in f:
            total_rows += 1
            fields = line.rstrip("\r\n").split("|")
            if len(fields) <= country_idx:
                continue
            if fields[country_idx] != "United Kingdom":
                continue
            owner_name = fields[name_idx]
            if owner_name in seen_owner_names:
                continue
            seen_owner_names.add(owner_name)
            tokens = {t.upper() for t in TOKEN_RE.findall(owner_name)}
            for token in tokens:
                if len(token) <= 2 or token in STOPWORDS:
                    continue
                token_counts[token] += 1
    print(f"{total_rows} rows scanned; {len(seen_owner_names)} distinct UK owner names")
    return token_counts


def main() -> None:
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <path-to-opendatadomestic.zip>", file=sys.stderr)
        raise SystemExit(1)
    zip_path = Path(sys.argv[1])
    token_counts = mine(zip_path)
    personal_names = build_personal_name_blocklist()
    before = len(token_counts)
    for name in personal_names:
        del token_counts[name]
    print(f"removed {before - len(token_counts)} personal-name tokens")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", encoding="utf-8") as out:
        out.write("token\tdistinct_owner_name_count\n")
        for token, count in token_counts.most_common():
            out.write(f"{token}\t{count}\n")
    print(f"Wrote {OUTPUT} ({len(token_counts)} distinct tokens)")


if __name__ == "__main__":
    main()
