# Spec: Add Scaladoc to purerest's public API, published alongside releases

## Overview
Completes Iteration 2's "make purerest a real, consumable library" goal by (1) filling in
missing Scaladoc comments across purerestlib's public API and (2) publishing the rendered
docs to a browsable GitHub Pages site, updated on every release. Note: a real Scaladoc jar
is already auto-published as a Maven `-javadoc.jar` artifact via sbt's default publish
behavior (discovered during spec-ing this track) — that mechanism is unaffected; this track
adds comment coverage and a browsable hosted copy.

## Functional Requirements
1. Add Scaladoc (`/** ... */`, brief one/two-line description, no `@param`/`@return`) to every
   public class/object/trait/def in `modules/purerestlib/src/main/scala/**` currently missing
   it — `HttpClient.scala` has none today; other files are partially covered.
2. Extend `.github/workflows/release.yml`: after the existing test gate passes, add a step
   that runs `sbt purerestlib/doc` and deploys the generated HTML to GitHub Pages (via
   `actions/upload-pages-artifact` + `actions/deploy-pages`), overwriting one stable "latest"
   URL on every `v*` tag push.
3. GitHub Pages must be enabled once, manually, by the user (Settings -> Pages -> Source:
   GitHub Actions) — a documented prerequisite, not something the workflow configures itself.
4. Update `README.md` and `conductor/tech-stack.md` to link to and describe the hosted
   Scaladoc URL.

## Non-Functional Requirements
- No changes to Scala package namespace, runtime behavior, or the existing
  `publishTo`/`-javadoc.jar` Maven publishing mechanism — comments and a new CI step only.
- Doc comments stay brief ("what", not "why") — consistent with this repo's own comment
  philosophy, applied to the public-API audience Scaladoc serves.

## Acceptance Criteria
- `sbt purerestlib/doc` runs cleanly with no new warnings.
- Every public class/object/trait/def in purerestlib's main sources has a Scaladoc comment.
- Pushing a real `v*` test tag triggers the workflow to build and deploy Scaladoc to GitHub
  Pages at a stable URL (verified live, tag cleaned up afterward — same convention as
  `release-pipeline_20260925`).
- The Maven package's `-javadoc.jar` artifact is still published, unaffected (regression
  check).
- README.md/tech-stack.md link to and describe the hosted docs URL.

## Out of Scope
- Versioned/per-release doc paths (deferred — latest-only for now).
- Auto-enabling GitHub Pages via the API (user does this manually, once).
- Full javadoc-style `@param`/`@return`/usage-example annotations (brief descriptions only).
