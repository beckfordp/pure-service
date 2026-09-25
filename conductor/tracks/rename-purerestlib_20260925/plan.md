# Plan: Rename purerest module to purerestlib; rename repo/project to purerest

## Phase 1: Rename the sbt module
- [ ] Task: `git mv modules/purerest modules/purerestlib`. In `build.sbt`: rename the `purerest` val to `purerestlib`, update `.in(file("modules/purerestlib"))`, `name := "purerest"` -> `name := "purerestlib"`. Update `.dependsOn(purerest, ...)` in order-service/inventory-service to `.dependsOn(purerestlib, ...)`.
- [ ] Task: Run `sbt compile test` — confirm everything still builds/passes with the renamed module (package names inside are untouched, so this should be mechanical).
- [ ] Task: Update `smoke-test/build.sbt`'s dependency coordinate and comments/error message (`purerest` -> `purerestlib`); confirm `scripts/verify-purerest-consumption.sh`-style flow still makes sense conceptually (script itself is archived, not touched).
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Rename the sbt module' (Protocol in workflow.md).

## Phase 2: Rename the root project and update CI/publish references
- [ ] Task: In `build.sbt`: `name := "pure-service"` -> `name := "purerest"` on the root project; `.aggregate(purerest, ...)` -> `.aggregate(purerestlib, ...)`; update the GitHub Packages `publishTo` URL to `.../beckfordp/purerest`.
- [ ] Task: Update `.github/workflows/release.yml`'s `sbt purerest/publish` -> `sbt purerestlib/publish`.
- [ ] Task: Run `sbt compile test` again to confirm the root-project rename didn't break anything.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Rename the root project and CI references' (Protocol in workflow.md).

## Phase 3: Rename the GitHub repo and update documentation
- [ ] Task: Rename the GitHub repository — requires explicit user go-ahead, since this changes the live remote repo — via `gh repo rename purerest`; then `git remote set-url origin` locally to the new URL and confirm `git fetch` succeeds.
- [ ] Task: Update `README.md`, `conductor/product.md` (mark the 'Future Direction' rename note as done), and `conductor/tech-stack.md` for the new repo name and `purerestlib` task paths.
- [ ] Task: Reconcile `conductor/tracks/release-pipeline_20260925/plan.md` and `spec.md` (still open, not archived) so their recorded trail matches the renamed module/repo.
- [ ] Task: Final repo-wide grep for `pure-service` outside `conductor/archive/`, `scripts/archive/`, `target/`, `.git/` — confirm none remain.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: GitHub repo rename and documentation' (Protocol in workflow.md).
