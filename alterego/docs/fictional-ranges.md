# Fictional-range provenance

Every "fictional by default" guarantee in SPECIFICATION.md section 4.1 rests on an external fact:
an officially reserved range, a documented allocation rule, or a structural impossibility. This
file records the provenance of each such rule — the source, retrieval date, and the exact
statement relied on — the same discipline as `docs/phone-ranges.md`. Where only a secondary
source was to hand, that is stated and the primary source is named for confirmation.

Retrieved: 2026-07-26 (except `phone-ranges.md`, dated there).

Rules with self-contained provenance, not repeated here:

- **`emailAddress()`** — RFC 2606 reserves `example.com` / `.net` / `.org` (and the `.test`,
  `.invalid` TLDs) for documentation and testing. The RFC is the citation.
- **`phoneNumber()`** — Ofcom drama ranges; full provenance in `docs/phone-ranges.md`.
- **Deferred** — TEST-NET IP ranges (RFC 5737) and `.test`/`.invalid` domains (RFC 6761) are
  likewise self-citing when implemented.

## postcode() — inward-code letters `C I K M O V`

- **Guarantee**: the last two letters of a real UK postcode are drawn only from
  `ABDEFGHJLNPQRSTUWXYZ`; `C I K M O V` are never used there. Forcing the final letter to one of
  those six yields a structurally invalid (undeliverable) postcode.
- **Reason (per the source)**: those six were excluded so sorting machines and readers cannot
  confuse similar-looking characters at speed.
- **Source**: gov.uk, "Appendix C — Valid Postcode Format" (ILR specification), which gives the
  regex with the final-two-letter set `[ABD-HJLNP-UW-Z]`. Corroborated by Wikipedia, "Postcodes
  in the United Kingdom", and ideal-postcodes.co.uk's "UK Postcode Format" guide.
- **URL**: https://assets.publishing.service.gov.uk/media/5a81ebbded915d74e6234d42/Appendix_C_ILR_2017_to_2018_v1_Published_28April17.pdf

## nhsNumber() — `999` reserved test range

- **Guarantee**: NHS numbers in `999 000 0000`–`999 999 9999` are reserved for test/synthetic
  data and are never issued to a real patient.
- **Source**: NHS England Digital, "Synthetic data in live environments" (NHS e-Referral
  Service). Statement relied on: "Synthetic patient records ... always start 999 ... The NHS
  number is valid but is from a range of numbers from which real NHS numbers will never be
  issued." (Real synthetic records also use family names beginning `XXTESTPATIENT`.)
- **URL**: https://digital.nhs.uk/services/e-referral-service/document-library/synthetic-data-in-live-environments
- **Check digit**: standard mod-11, weights 10..2 over the first nine digits, `11 - (sum mod 11)`,
  with `11 -> 0` and `10` meaning an invalid number (redraw). Widely documented; corroborated by
  the `wardle.org` NHS-number write-up and multiple open-source validators.

## nationalInsuranceNumber() — `QQ` unallocatable prefix

- **Guarantee**: `Q` is never used as the first (or second) letter of an allocated NINO prefix,
  and `QQ` is the prefix HMRC uses for its own examples.
- **Source**: gov.uk, HMRC National Insurance Manual NIM39110 ("Format and Security: What a NINO
  looks like") and the gov.uk National Insurance page. Statements relied on: the characters
  `D, F, I, Q, U, V` are not used as the first or second letter of a prefix; `O` is not used as
  the second letter; example "QQ 12 34 56 A ... This is an example only and should not be used as
  an actual number." Suffix letter is always `A`, `B`, `C`, or `D`.
- **URL**: https://www.gov.uk/hmrc-internal-manuals/national-insurance-manual/nim39110

## creditCardNumber() — ISO/IEC 7812 major industry identifier `0`

- **Guarantee**: the first digit (major industry identifier) `0` is reserved for ISO/TC 68 and
  future assignment; no payment card scheme issues PANs beginning `0`.
- **Source**: ISO/IEC 7812-1 (Identification cards — Identification of issuers), corroborated by
  the Wikipedia ISO/IEC 7812 article. Statement relied on: MII `0` is designated for "ISO/TC 68
  and other future industry assignments."
- **URL**: https://en.wikipedia.org/wiki/ISO/IEC_7812 (primary: ISO/IEC 7812-1:2006, paywalled)
- **Check digit**: standard Luhn over the first fifteen digits.

## passportNumber() — `ZZ` prefix impossible for a UK passport

- **Guarantee**: UK passport numbers are wholly numeric (9 digits), so a value carrying letters
  can never be a valid UK passport number, while still passing generic passport-field validation
  (up to 9 alphanumeric characters).
- **Source**: HM Passport Office guidance via gov.uk ("Basic passport checks"); UK passport
  numbers have been 9 numeric digits since 1988. Statement relied on: the passport number is the
  9-digit numeric code at the top of the photo page.
- **URL**: https://assets.publishing.service.gov.uk/media/5a7a3bc5ed915d1a6421c00a/basic-passport-checks.pdf

## drivingLicenceNumber() — `99999` surname block impossible

- **Guarantee**: in the DVLA (Great Britain) 16-character format, characters 1-5 encode the
  surname, `9`-padded only *after* the surname's own letters; a real surname always contributes
  at least one letter, so `99999` (a zero-letter surname) can never occur on a real licence.
- **Source**: DVLA driving-licence-number format, widely documented (secondary sources:
  LegalClarity, Confused.com). Statement relied on: "The first 5 characters are your surname; if
  your surname is fewer than 5 letters, the remaining spaces are filled with the number 9."
  DOB block (chars 6-11): decade digit, month (+50 for female), day, year-unit digit — e.g. a
  male born 23 March 1986 gives `803236`, a female `853236`. Chars 12-13 are initials
  (`9` if a single forename); chars 14-16 are randomised check characters.
- **Primary source to confirm**: DVLA is the authority; the exact composition of the chars 14-16
  check block is not published, but is immaterial here — the guarantee rests solely on `99999`.
- **Note**: Northern Ireland's separate DVA format (8 digits) is not generated; output is always
  the Great Britain layout.

## companyNumber() — DEFERRED (unsolved fictional space)

**Not implemented. Open issue: no acceptable fictional space has been found.** Companies House
registration numbers have no officially reserved test range (unlike NHS numbers) and no checksum;
they are allocated sequentially from 1.

- The only permanent structural impossibility is the number **zero** (`00000000` / `SC000000` /
  `NI000000`) — but mapping every input to zero is **redaction, not pseudonymisation**: distinct
  companies collapse to one value per nation and `unique()` cannot apply. Rejected as a solution.
- A high range (e.g. numbers starting `9`) is **time-dependent** and already marginal: Scotland's
  6-digit space is at `SC770005` (2026), so `SC9xxxxx` is only ~a decade away; it fails the
  project's no-time-dependence invariant regardless.
- There is no reserved or invalid range in between.

Revisit if a reserved/never-issued range comes to light. Format facts (for the regional element,
should it return): England & Wales are 8 digits with no distinguishing prefix; Scotland is `SC` +
6 digits; Northern Ireland is `NI` + 6 digits; always 8 characters total.
Source: gov.uk / Companies House; HMRC "Company Registration Number Formats"
(https://www.hmrc.gov.uk/gds/com/attachments/coy_reg_no_formats.doc); current-allocation
observations from live Companies House records (2026).
