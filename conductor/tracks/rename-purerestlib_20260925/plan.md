# Plan: Rename purerest module to purerestlib; rename repo/project to purerest

## Phase 1: Rename the sbt module
- [x] Task: `git mv modules/purerest modules/purerestlib`. In `build.sbt`: rename the `purerest` val to `purerestlib`, update `.in(file("modules/purerestlib"))`, `name := "purerest"` -> `name := "purerestlib"`. Update `.dependsOn(purerest, ...)` in order-service/inventory-service to `.dependsOn(purerestlib, ...)`. Also had to update `.aggregate(purerest, ...)` -> `.aggregate(purerestlib, ...)` in this same task (not Phase 2 as originally planned) — the val rename makes build.sbt fail to load at all otherwise, since it's one file with one set of project definitions. [5d59948]
- [x] Task: Run `sbt compile test` — confirmed: 29 passed, 0 failed, mechanical rename as expected.
- [x] Task: Update `smoke-test/build.sbt`'s dependency coordinate and comments/error message (`purerest` -> `purerestlib`); `scripts/verify-purerest-consumption.sh` is archived, left untouched per spec. [5d59948] (same commit as the module rename — staged together)
- [x] Task: Conductor - User Manual Verification 'Phase 1: Rename the sbt module' (Protocol in workflow.md). Satisfied by the `sbt compile test` run above — prompting is off (see `/prompt`), substituting for the interactive walkthrough; no dedicated verify script exists for a build-identifier rename.

## Phase 2: Rename the root project and update CI/publish references
- [x] Task: In `build.sbt`: `name := "pure-service"` -> `name := "purerest"` on the root project (`.aggregate` already updated in Phase 1, forced by the val rename); update the GitHub Packages `publishTo` URL to `.../beckfordp/purerest`. [030ad5c]
- [x] Task: Update `.github/workflows/release.yml`'s `sbt purerest/publish` -> `sbt purerestlib/publish`. [030ad5c] (same commit — YAML syntax re-validated)
- [x] Task: Run `sbt compile test` again to confirm the root-project rename didn't break anything. Verified: 29 passed, 0 failed.
- [x] Task: Conductor - User Manual Verification 'Phase 2: Rename the root project and CI references' (Protocol in workflow.md). Satisfied by the `sbt compile test` run + YAML validation above — prompting is off (see `/prompt`).

## Phase 3: Rename the GitHub repo and update documentation
- [ ] Task: Rename the GitHub repository — requires explicit user go-ahead, since this changes the live remote repo — via `gh repo rename purerest`; then `git remote set-url origin` locally to the new URL and confirm `git fetch` succeeds.
- [ ] Task: Update `README.md`, `conductor/product.md` (mark the 'Future Direction' rename note as done), and `conductor/tech-stack.md` for the new repo name and `purerestlib` task paths.
- [ ] Task: Reconcile `conductor/tracks/release-pipeline_20260925/plan.md` and `spec.md` (still open, not archived) so their recorded trail matches the renamed module/repo.
- [ ] Task: Final repo-wide grep for `pure-service` outside `conductor/archive/`, `scripts/archive/`, `target/`, `.git/` — confirm none remain.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: GitHub repo rename and documentation' (Protocol in workflow.md).
