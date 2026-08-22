# Task: consolidate subproject documentation into root docs

Status: not started; **four phases, complete one at a time** — see §3–§6. A repo-wide migration
handoff; delete once all phases are done and `doc-kit-check.sh` reports conformant.

## 1. Why

`alterego`, `incognito`, and `effigies` converged independently on a near-doc-kit-shaped structure
(own `SPECIFICATION.md`, `PLAN.md`, `docs/adr/`, `CHANGELOG.md`), but the project has decided to
consolidate to a single repo-wide documentation set rather than keep the per-module default.
Reasoning: the three subprojects are lockstep-versioned (one version number, one tag, one release
for all three) — the case where per-module docs earn their keep least.

## 2. Decisions already made (do not re-litigate)

- Root `DOC-MAP.md`: yes, one map for the whole monorepo.
- Root `SPECIFICATION.md` becomes an index; the three subprojects' contracts move to
  `docs/spec/alterego.md` / `incognito.md` / `effigies.md` unchanged, content-wise.
- `AGENTS.md` is canonical (not `CLAUDE.md`); merge `CLAUDE.md`'s content into it, then leave
  `CLAUDE.md` as a one-line pointer.
- `PLAN.md` entries get a `**Project:**` tag (`alterego` / `incognito` / `effigies`); cross-cutting
  or unknown-project entries get no tag.
- ADRs renumber `0001`–`0023` chronologically by accepted date (module tie-break for same-day
  batches: alterego → incognito → effigies), reformatted to doc-kit's MADR/YAML shape. The tripled
  "Lockstep versioning" record (`alterego/0015`, `incognito/0008`, `effigies/0002` — same decision,
  same date) collapses to one merged record.
- Pre-lockstep `CHANGELOG.md` history merges into the root file with a project-prefixed version tag
  (`alterego-0.1.0` … `alterego-0.4.0`, `incognito-1.0.0`, `effigies-1.0.0`) — necessary, not just
  tidy: `incognito` and `effigies` each had their own `1.0.0` before joining lockstep, which would
  otherwise collide with the root file's own first lockstep release, also `[1.0.0]`.

## 3. Phase 1 — Root skeleton (low risk, no renumbering)

- [x] Write root `DOC-MAP.md`.
- [x] Root `SPECIFICATION.md` → index; move the three subprojects' `SPECIFICATION.md` content to
      `docs/spec/alterego.md` / `incognito.md` / `effigies.md` (via `git mv`, content unchanged
      except the handful of relative links that pointed at sibling directories, fixed for the new
      location).
- [x] Merge `CLAUDE.md` into `AGENTS.md` (nothing thrown away, `alterego`/`incognito`/`effigies`
      hard-invariant ADR citations generalised to non-numbered pointers pending Phase 4's
      renumbering); replace `CLAUDE.md` with a pointer.
- [x] Fixed the resulting broken links: three subproject `README.md` files linked
      `[SPECIFICATION.md](SPECIFICATION.md)` in their own directory; root `README.md`'s prose named
      the old per-subproject location.

**Newly discovered, not in scope for Phase 1**: ~50 Java source files (mostly `alterego` Javadoc)
cite `SPECIFICATION.md section N` as plain text, not a link — stale (file moved to
`docs/spec/<subproject>.md`) but not broken (nothing reads the path programmatically; checked
`alterego/tools/verify_vectors.py`, `build.gradle.kts` comments, and a sample of the Javadoc sites).
Low priority; candidate for a bulk find-and-replace pass, own decision on when.

## 4. Phase 2 — `PLAN.md` consolidation (mechanical, low ambiguity)

- [ ] Merge the three subprojects' `PLAN.md` entries into root `PLAN.md`, tagging each with
      `**Project:**`.
- [ ] Move any existing `<subproject>/docs/tasks/*.md` file to root `docs/tasks/`, prefixed with its
      subproject (e.g. `incognito/docs/tasks/composite-pk-cyclic-fk.md` →
      `docs/tasks/incognito-composite-pk-cyclic-fk.md`), and update the moved `PLAN.md` entry's
      pointer to match. Repo-specific to this migration, not a naming convention doc-kit itself
      needs. Only one such file exists today: `incognito/docs/tasks/composite-pk-cyclic-fk.md`.
- [ ] Delete the three subproject `PLAN.md` files.

## 5. Phase 3 — `CHANGELOG.md` consolidation (mechanical, needs the prefix scheme)

- [ ] Fold `alterego` `[0.1.0]`–`[0.4.0]`, `incognito` `[1.0.0]`, `effigies` `[1.0.0]` into the root
      file as `alterego-0.1.0` … `alterego-0.4.0`, `incognito-1.0.0`, `effigies-1.0.0`, in original
      chronological order, ahead of the existing lockstep `[1.0.0] — 2026-08-10`.
- [ ] Delete the three subproject `CHANGELOG.md` files.

## 6. Phase 4 — ADR renumbering (highest risk, largest blast radius — do last, separately)

- [ ] Build and confirm the exact old-number → new-number mapping table (23 records) before touching
      any file.
- [ ] Merge, renumber, and reformat every ADR into doc-kit's MADR/YAML shape.
- [ ] Update every citation of an old `ADR NNNN` number across the 32 files found referencing one
      (README ×3 + root, `SPECIFICATION.md` ×3 / now `docs/spec/*.md`, `PLAN.md` and `CHANGELOG.md`
      entries, and ~15 Java source files' Javadoc/comments).
- [ ] Delete the three subproject `docs/adr/` directories.

## 7. Definition of done

- `doc-kit-check.sh map adr plan` (run from doc-kit against this repo) reports conformant.
- No remaining reference anywhere in the repo (docs or source) to a pre-renumbering ADR number.
- `./gradlew build` still green — this is a docs-only migration; if it isn't, something moved that
  shouldn't have.

## 8. Risk note

Phase 4 is the one that can silently break something: a missed Javadoc citation doesn't fail the
build, so the mapping table must be checked, not just produced, before any file is edited.
