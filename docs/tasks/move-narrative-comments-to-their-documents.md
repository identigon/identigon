# Task: move narrative comments to the documents they belong in

Status: started - main-code history comments in `incognito` and `effigies`, and the root and
subproject Gradle scripts, are done. Delete once the rest is done.

## 1. The rule

`DOC-MAP.md` files a sentence by its tense. Code comments should state what the code does and why,
**now**. Three other kinds of sentence keep turning up in comments instead:

- **"X used to be Y"** - history. It belongs in `CHANGELOG.md` (usually already there) or the commit
  message, and should be deleted or rewritten in present tense ("without this check, ...").
- **"We chose X because Y, not Z"** - a decision with a rejected alternative. If a newcomer would
  question it, it belongs in an ADR; the comment keeps one line and a pointer.
- **"TODO: do X"** - intent. It belongs in `PLAN.md`; the comment keeps at most a pointer.

A comment explaining a non-obvious constraint of the current code ("pg_get_serial_sequence's
arguments are not symmetric") is correct where it is - leave it.

## 2. What is left

- **Test code**: 21 history-narrating comments across 18 test classes - mostly "Regression test: X
  used to ...". Keep the regression framing (it says why the test exists) but state the guarded
  behaviour in present tense and drop the history. Find them with:

  ```sh
  grep -rEn '(//|\*).*\b([Pp]reviously|[Uu]sed to\b|[Nn]o longer|was removed|[Rr]egression test)' \
    */src/test --include=*.java
  ```

- **Config**: `.pre-commit-config.yaml` (about 80 comment lines) and the workflows carry long
  rationale paragraphs. Most are genuine present-tense constraints (why a hook is `local`, why a
  file is excluded); trim the ones that recount how a problem was discovered.
- **Rationale that is really a decision**: long comment blocks weighing alternatives, e.g. the
  `identigonJar` block in `effigies/build.gradle.kts` (already covered by ADR-0028/0030 - reduce it
  to a pointer). Look for "rather than", "instead of", "deliberately" in comments over three lines.
- **Spec intent**: `docs/spec/incognito.md` §10 says an `incognito-yaml` module split "is planned".
  That is plan-tense in the spec; it is now a `PLAN.md` entry, so the spec sentence can drop the
  intent the next time that section changes for a behavioural reason (the spec follows the work).

## 3. How

One subproject per commit (`docs:` or `style:`), no behaviour change, `./gradlew build` green. Where
a comment's history is not yet in `CHANGELOG.md` and someone upgrading would care, add the changelog
line rather than losing it.
