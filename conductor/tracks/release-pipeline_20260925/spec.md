# Spec: Add a tag-triggered CI release pipeline that publishes purerest to a real package repository

## Overview
Adds a GitHub Actions workflow that, on push of a `v*`-tagged commit, runs the full test suite
and then publishes purerest to GitHub Packages (Maven registry) instead of only the local Ivy2
cache — closing the release-automation half of Iteration 2's "make purerest a real, consumable
library" goal (Scaladoc publishing is a separate backlog item).

## Functional Requirements
1. New GitHub Actions workflow (`.github/workflows/release.yml`), triggered on `push` of tags
   matching `v*`.
2. Steps: checkout with full git history/tags (sbt-dynver needs tag depth) -> set up JDK+sbt ->
   run `sbt scalafmtCheck test` across the whole build -> only if that passes, run
   `sbt purerest/publish`.
3. `purerest`'s `build.sbt` gains `publishTo`/`credentials` settings pointing at this repo's
   GitHub Packages Maven registry, authenticated via the workflow's built-in `GITHUB_TOKEN` (no
   new secrets to manage).
4. sbt-dynver (already in place) derives the published version from the pushed tag; `v1.2.3`
   publishes as `1.2.3` (early-semver, matching purerest's existing `versionScheme`).
5. Document the workflow, tag convention, and how a consumer resolves the artifact (GH Packages
   resolver + PAT with `read:packages`) in `tech-stack.md` and `README.md`.

## Non-Functional Requirements
- No changes to purerest's own code/API — CI/build-config only.
- The workflow fails loudly if either the test gate or the publish step fails — no silent
  partial releases.

## Acceptance Criteria
- Pushing a `v*` tag triggers the workflow (verified with a real test tag, e.g. `v0.0.1-ci-test`,
  deleted after confirming).
- The workflow fails without publishing if `sbt scalafmtCheck test` fails (verified by
  temporarily breaking a test/format check).
- On success, `purerest` for that version is visible in the repo's GitHub Packages page as a
  Maven artifact.
- `smoke-test/`'s existing standalone build can resolve and use that exact published version
  from GitHub Packages (not just local Ivy2), proving it's genuinely consumable externally.
- `tech-stack.md`'s "No CI/release automation yet" line is updated to describe the new pipeline.

## Out of Scope
- Publishing Scaladoc (separate backlog item).
- Publishing order-service/inventory-service artifacts — only purerest.
- Maven Central / Sonatype publishing.
- General CI (build/test on every push/PR) — this track is the release pipeline only.
