# Plan: Add a tag-triggered CI release pipeline that publishes purerest to a real package repository

## Phase 1: Configure GitHub Packages publishing in build.sbt
- [ ] Task: Add `publishTo`/`credentials` settings to purerest's `build.sbt` targeting this repo's GitHub Packages Maven registry, reading actor/token from env vars (`GITHUB_ACTOR`/`GITHUB_TOKEN`) so they're unset (and harmless) for local dev.
- [ ] Task: Confirm `sbt purerest/publishLocal` and the existing local dev flow are unaffected by the new settings (env vars unset locally).
- [ ] Task: Conductor - User Manual Verification 'Phase 1: GitHub Packages publishing config' (Protocol in workflow.md).

## Phase 2: Tag-triggered release workflow
- [ ] Task: Create `.github/workflows/release.yml` — triggers on push of tags matching `v*`; steps: checkout (full history/tags), set up JDK + sbt, run `sbt scalafmtCheck test`, then (only on success) `sbt purerest/publish`, with `permissions: packages: write`.
- [ ] Task: Confirm the repo's Actions settings allow `GITHUB_TOKEN` to write packages (Settings -> Actions -> General -> Workflow permissions); adjust if needed.
- [ ] Task: Push a real test tag (e.g. `v0.0.1-ci-test`) — requires explicit user go-ahead, since this pushes to the shared remote and triggers a real CI run — confirm the workflow runs end-to-end and the package appears on the repo's GitHub Packages page. Clean up the test tag/package afterward.
- [ ] Task: Confirm the test gate actually blocks publishing on failure (temporarily break a test on a disposable branch/tag, confirm the job stops before the publish step, then revert/delete).
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Tag-triggered release workflow' (Protocol in workflow.md).

## Phase 3: External resolution + documentation
- [ ] Task: Point `smoke-test/`'s standalone build at GitHub Packages (documented flag/profile) and confirm it resolves and runs against the tag-published version, not just the local Ivy2 cache.
- [ ] Task: Update `tech-stack.md`'s Publishing section (remove "No CI/release automation yet", document the pipeline/tag convention/GH Packages resolution) and README.md (how a consumer adds the resolver + PAT).
- [ ] Task: Conductor - User Manual Verification 'Phase 3: External resolution + documentation' (Protocol in workflow.md).
