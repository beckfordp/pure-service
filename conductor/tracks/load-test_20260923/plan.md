# Plan: Add a Gatling-based load-test module

## Phase 1: Gatling Load-Test Module
- [ ] Task: Add `modules/load-test` subproject to `build.sbt` (`enablePlugins(GatlingPlugin)`, pinned Gatling deps) and the `gatling-sbt` plugin to `project/plugins.sbt`; run `sbt projects` to confirm `load-test` is listed, and `sbt compile`/`sbt test` at the repo root to confirm it doesn't run any load test as part of the normal loop.
- [ ] Task: Write `OrderPlacementSimulation` (repeated `POST /orders`, configurable base URL, ramp-then-hold injection profile as spec'd).
- [ ] Task: Manually start order-service + inventory-service (docker-compose + healthy config) and run `sbt load-test/Gatling/test` directly; confirm the simulation completes with 0% failed requests and produces an HTML report under `modules/load-test/target/gatling/`.
- [ ] Task: Add `scripts/loadtest-purerest.sh` (two-pass orchestration: healthy run printing RED metric summaries, then a degraded-failure-rate run printing resilience metric summaries) and run it end-to-end.
- [ ] Task: Document `modules/load-test` and `scripts/loadtest-purerest.sh` in `tech-stack.md`.
- [ ] Task: Run `sbt compile`/`sbt test` across purerest/order-service/inventory-service to confirm no regressions from adding the new subproject.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Gatling Load-Test Module' (Protocol in workflow.md)
