# Task: plainer names in the policy vocabulary

Status: not started. Needs an ADR before implementation. Delete once implemented and green.

## 1. The gap

User feedback singles out the `ALTEREGO_` prefix on `directIdStrategy` values as extra cognitive
load: it names an internal library, not what happens to the data. More generally, a policy author
has to learn a vocabulary built around the engine's Java API rather than around their decision.

## 2. The vocabulary today

From `docs/spec/incognito.md` §4.1, §6 and Appendix B, and the enums in
`incognito/src/main/java/org/identigon/incognito/api/`:

| Key                 | Applies to                                                                      | Values                                                                                                                                                                                                                                                                                                                                                   |
| ------------------- | ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `role`              | every column                                                                    | `PRIMARY_KEY`, `FOREIGN_KEY`, `UNIQUE_CANDIDATE_KEY`, `DIRECT_ID`, `QUASI_ID`, `SENSITIVE`, `PAYLOAD`, `INHERITED_ATTRIBUTE`, `GENERATED_COLUMN` (+ 5 reserved that parse but fail)                                                                                                                                                                      |
| `surrogateStrategy` | `PRIMARY_KEY`                                                                   | `SEQUENTIAL_LONG`, `PASSTHROUGH_SURROGATE`                                                                                                                                                                                                                                                                                                               |
| `directIdStrategy`  | `DIRECT_ID`, `UNIQUE_CANDIDATE_KEY`; also a "hint" on `QUASI_ID` + `SYNTHESISE` | `ALTEREGO_NAME`, `ALTEREGO_FIRST_NAME`, `ALTEREGO_LAST_NAME`, `ALTEREGO_ORGANISATION`, `ALTEREGO_CITY`, `ALTEREGO_STREET_ADDRESS`, `ALTEREGO_POSTCODE`, `ALTEREGO_EMAIL`, `ALTEREGO_PHONE`, `ALTEREGO_DOMAIN`, `ALTEREGO_URL`, `ALTEREGO_NINO`, `ALTEREGO_NHS_NUMBER`, `ALTEREGO_PASSPORT_NUMBER`, `ALTEREGO_DRIVING_LICENCE_NUMBER`, `ALTEREGO_GENERIC` |
| `quasiIdStrategy`   | `QUASI_ID`; `SENSITIVE` with `distinguishing: true`                             | `SYNTHESISE`, `JITTER_DAYS`, `JITTER_WITHIN_MONTH`, `JITTER_WITHIN_YEAR`                                                                                                                                                                                                                                                                                 |
| `redactionStrategy` | `SENSITIVE` with `distinguishing: true`                                         | `CLEAR`, `MASK`, `CONSTANT`                                                                                                                                                                                                                                                                                                                              |
| `distinguishing`    | `SENSITIVE` (required)                                                          | `true`, `false`                                                                                                                                                                                                                                                                                                                                          |
| others              | per strategy                                                                    | `jitterDays`, `coherenceGroup`, `redactionConstant`, `references`, `derivedFrom`                                                                                                                                                                                                                                                                         |

Friction points:

- `ALTEREGO_` is on every value in one enum and on no other enum.
- `ALTEREGO_GENERIC` says nothing about what it does (shape-preserving, no fictionality guarantee),
  which is the one thing an author most needs to know about it.
- `directIdStrategy` doubles as the generator hint on a `QUASI_ID` column (ADR 31), so its name is
  wrong in half of its uses.
- Five role-specific strategy keys, one per role, chosen deliberately in spec §6 to remove ambiguity
  about which enum applies.

## 3. Scope for the ADR

The core decision: drop the `ALTEREGO_` prefix from the policy's spelling (`NAME`, `EMAIL`, `NINO`,
...), and give `ALTEREGO_GENERIC` a name that says what it does (e.g. `SHAPE_PRESERVING`). Also to
decide:

- **Compatibility.** Keep accepting the old spellings as deprecated aliases (with a `validate`
  warning and a changelog entry), or make a clean break at the next major version? Existing policies
  in the repository (quickstart, four benchmark fixtures, the agent skill's examples) show how much
  depends on the old names.
- **YAML only, or the Java enum too?** `DirectIdStrategy` is public `incognito` API; renaming its
  constants breaks library callers. The YAML spelling can change on its own through a name mapping
  in `YamlPolicyParser`.
- **Generator key on `QUASI_ID`.** Whether the hint gets its own neutral key (e.g. `generator:`)
  instead of borrowing `directIdStrategy`.
- **Considered but not proposed:** collapsing the role-specific keys into one `strategy:`. Spec §6
  chose role-specific keys on purpose; list it as an option and say why it was rejected, or
  supersede that choice explicitly.
- **Role names** (`DIRECT_ID`, `QUASI_ID`, `SENSITIVE`/`distinguishing`, `PAYLOAD`) are jargon too,
  but they are the privacy model's own terms (§2, ADR 14, ADR 16). Explaining them is the
  self-explaining-scaffold and classification-guide tasks' job; renaming them is out of scope here
  unless the ADR decides otherwise.

## 4. Constraints to check before implementing

- Confirm no enum constant name feeds keyed derivation or any `alterego` domain (`"alterego:..."`)
  or attribute-key name - the parser lookup is `valueOf`-style, but check that no `.name()` reaches
  a derivation input. If one does, renaming the constant would silently change every user's output
  (`alterego` hard invariants 1 and 6). The YAML-only option avoids this by construction.
- Unrecognised keys and values still fail closed (spec §6). An alias table is an explicit
  allow-list, like the existing `autoInfer` exception, not a relaxed parse.
- Update in the same change: `docs/spec/incognito.md` §4.1/§6/Appendix B, the effigies scaffold and
  inferrer suggestions, `quickstart/policy.yaml`, the benchmark policies, the agent skill, and
  `CHANGELOG.md`.
