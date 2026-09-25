# Plan: Add Scaladoc to purerest's public API, published alongside releases

## Phase 1: Scaladoc comment coverage [checkpoint: d4aa59f]
- [x] Task: Audit `modules/purerestlib/src/main/scala/**` for every public class/object/trait/def currently missing a Scaladoc comment (grep-based pass, produce a checklist by file). Found 12 gaps: `HttpClient.scala` (`object HttpClient`, `def resource`), `Docs.scala` (`def routes`), `Logging.scala` (`object Logging`), `Metrics.scala` (`object Metrics`), `ClientMetrics.scala` (`object ClientMetrics`), `ServerMetrics.scala` (`object ServerMetrics`), `Resilience.scala` (`ResilienceConfig`, `def middleware`), `Retry.scala` (`isRetriableError`, `isRetriableResponse`, `def middleware`), `CircuitBreaker.scala` (`def middleware`), `Tracing.scala` (`object Tracing`), `ClientTracing.scala` (`object ClientTracing`), `ServerTracing.scala` (`object ServerTracing`).
- [x] Task: Add brief (one/two-line, no @param/@return) Scaladoc comments to all currently-undocumented public symbols found in the audit — `HttpClient.scala` (fully undocumented today), plus remaining gaps in `Docs.scala`, `Retry.scala`, `Resilience.scala`, `ServerTracing.scala`, `ClientTracing.scala`, `ServerMetrics.scala`, `ClientMetrics.scala`, `Logging.scala`. All 12 gaps from the audit filled. [5363ac5]
- [x] Task: Run `sbt purerestlib/doc` and `sbt purerestlib/compile` — confirm doc generation completes with no new warnings and compilation is unaffected. Both succeeded (`[success]`); the one `-classpath was updated` warning is a pre-existing sbt/scaladoc classpath notice, unrelated to the new doc comments. Generated pages confirmed under `target/scala-3.9.0/api/purerest/{client,docs,logging,metrics,resilience,tracing}`.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Scaladoc comment coverage' (Protocol in workflow.md). Prompting is off (see `/prompt`); satisfied by `scripts/verify-scaladoc-coverage.sh`, run directly — compile + doc generation succeeded and all 12 audited gaps confirmed documented. [5363ac5]

## Phase 2: GitHub Pages deployment workflow
- [ ] Task: Extend `.github/workflows/release.yml` with a docs-deploy job: after the existing test gate passes, run `sbt purerestlib/doc`, then `actions/upload-pages-artifact` (pointing at the generated `target/.../api` dir) and `actions/deploy-pages`, with the required `pages: write`/`id-token: write` permissions and a `github-pages` deployment environment. Docs publish to the Pages site root (`https://beckfordp.github.io/purerest/`), overwritten on every `v*` tag push.
- [ ] Task: Confirm GitHub Pages is enabled (Settings -> Pages -> Source: GitHub Actions) — manual prerequisite, done by the user before the first real deploy.
- [ ] Task: Push a real test tag — requires explicit user go-ahead — confirm the workflow runs end-to-end, the Pages deployment succeeds, and the Scaladoc site is reachable at the resulting URL. Clean up the test tag afterward.
- [ ] Task: Confirm the Maven package's `-javadoc.jar` artifact is still published as before (regression check against the same test-tag run).
- [ ] Task: Conductor - User Manual Verification 'Phase 2: GitHub Pages deployment workflow' (Protocol in workflow.md).

## Phase 3: Documentation
- [ ] Task: Update `README.md` with a link to and short description of the hosted Scaladoc site.
- [ ] Task: Update `conductor/tech-stack.md`'s Publishing section to mention the GitHub Pages Scaladoc deployment alongside the existing release-pipeline description.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Documentation' (Protocol in workflow.md).
