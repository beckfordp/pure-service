# Spec: Rename purerest module to purerestlib; rename repo/project to purerest

## Overview
Frees the name "purerest" for the repository/root-project level by renaming the library's own
sbt module (currently also called "purerest") to "purerestlib" — val identifier, directory, and
artifact id. The GitHub repo (`pure-service`) and the root sbt project both become "purerest".
Scala package names (`purerest.*`) are untouched — only build/publish/repo identifiers change.

## Functional Requirements
1. Rename the sbt module: val `purerest` -> `purerestlib`; `git mv modules/purerest
   modules/purerestlib`; `name := "purerest"` -> `name := "purerestlib"`. Update
   `.dependsOn(purerest, ...)` call sites in order-service/inventory-service to
   `purerestlib`.
2. Rename the root sbt project: `name := "pure-service"` -> `name := "purerest"`;
   `.aggregate(purerest, ...)` -> `.aggregate(purerestlib, ...)`.
3. Update `smoke-test/build.sbt`'s dependency coordinate (`"io.github.beckfordp" %%
   "purerest"` -> `%% "purerestlib"`) and its comments/error message referencing `sbt
   purerest/version`/`publishLocal`.
4. Update `.github/workflows/release.yml`'s `sbt purerest/publish` -> `sbt
   purerestlib/publish`, and `build.sbt`'s GitHub Packages `publishTo` URL
   (`.../beckfordp/pure-service` -> `.../beckfordp/purerest`).
5. Rename the GitHub repo via `gh repo rename purerest` (explicit go-ahead required right
   before running it), then `git remote set-url origin` to the new URL locally.
6. Update doc references to the old repo name and old `purerest/*` sbt task paths in
   `README.md`, `conductor/product.md`, `conductor/tech-stack.md`.
7. Reconcile the still-open `release-pipeline_20260925` track's own `plan.md`/`spec.md` (not
   archived) so its recorded trail matches the renamed module/repo.
8. Update `product.md`'s "Future Direction" note — this was "under consideration"; mark it
   done.

## Non-Functional Requirements
- No Scala package namespace changes (`purerest.*` stays) — this renames build/publish/repo
  identifiers only.
- `sbt test` stays fully green throughout.
- Archived docs (`conductor/archive/**`) and archived scripts (`scripts/archive/**`) are left
  untouched as historical record.
- `organization := "io.github.beckfordp"` (Maven groupId) is unaffected — tied to the GitHub
  username, not the repo name.

## Acceptance Criteria
- `sbt compile test` passes with the renamed module/project.
- `smoke-test/`'s standalone build resolves and runs against a `purerestlib`-coordinate jar.
- The GitHub repo is renamed to `purerest`; local `origin` remote points at the new URL and
  `git fetch` succeeds.
- No remaining `pure-service` references outside `conductor/archive/`, `scripts/archive/`,
  `target/`, `.git/` (verified by a final repo-wide grep).
- `release-pipeline_20260925`'s plan.md/spec.md reflect the renamed module/repo.

## Out of Scope
- Scala package namespace changes.
- Renaming the local working-directory folder on this machine (manual, machine-local step).
- Actually re-running the release pipeline end-to-end (still blocked on the GitHub billing
  issue) — this track only keeps its references consistent.
- Retroactively editing archived track docs/scripts.
- Any change to the Maven groupId.
