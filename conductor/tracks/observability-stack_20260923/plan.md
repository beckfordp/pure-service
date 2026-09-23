# Plan: Stand up a local production-like observability stack

## Phase 1: Enrich Logging
- [ ] Task: Add `log4cats-testing % Test` to order-service's and inventory-service's own `build.sbt` entries (purerest keeps it Test-scoped, which doesn't propagate via `.dependsOn`).
- [ ] Task: Write failing tests (Red) asserting request-received/completed log lines (with structured context) for order-service's `POST /orders` and `GET /orders/{id}` (success + `OrderNotFound` 404), using `StructuredTestingLogger.impl[IO]()`.
- [ ] Task: Implement the logging in `OrderRoutes.scala` to pass (Green).
- [ ] Task: Write failing tests (Red) asserting inventory-service's request logging and that `maybeInduceFailure`'s trigger logs a warn-level line with item/quantity context.
- [ ] Task: Implement inventory-service's logging to pass (Green).
- [ ] Task: Write failing tests (Red) asserting error-path logging (reservation/store failures) before they surface to the client.
- [ ] Task: Implement error-path logging to pass (Green).
- [ ] Task: Add startup config-summary logging (port, metrics port, induced-failure rate) to both `Main.scala`s — verified manually via console output, not unit-tested (side-effecting entrypoint).
- [ ] Task: Run `sbt coverage purerest/test orderService/test inventoryService/test coverageReport`; confirm 100% coverage on changed files, full suite green.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Enrich Logging' (Protocol in workflow.md)

## Phase 2: Enrich Metrics
- [ ] Task: Write a failing test (Red) asserting a live `purerest.circuit_breaker.state` gauge reflects CLOSED/OPEN correctly via `Metrics.test`'s testkit.
- [ ] Task: Implement the state gauge in `CircuitBreaker.middleware` to pass (Green).
- [ ] Task: Write a failing test (Red) — against order-service's real-Postgres integration setup — asserting a DB query duration measurement (with an operation attribute) is recorded for `OrderStore` queries, and an error is recorded on failure.
- [ ] Task: Implement DB query metrics in `OrderStore`, wired to the already-constructed `Meter[F]`, to pass (Green).
- [ ] Task: Run full test suite + coverage; confirm 100% on changed files.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Enrich Metrics' (Protocol in workflow.md)

## Phase 3: Containerize Services
- [ ] Task: Add `sbt-native-packager` `1.11.1`; enable `JavaAppPackaging`/`DockerPlugin` on both services (`dockerBaseImage`, `dockerUpdateLatest`).
- [ ] Task: Add `logback-docker.xml` to purerest (JSON via new `logstash-logback-encoder` Runtime dep); wire `Docker / javaOptions` to select it.
- [ ] Task: Run `sbt orderService/Docker/publishLocal inventoryService/Docker/publishLocal`; confirm images exist and briefly `docker run` each to confirm JSON stdout logging.
- [ ] Task: Confirm the existing `scripts/verify-*.sh` sbt-bgRun flow and `sbt compile`/`sbt test` at the repo root are unaffected.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Containerize Services' (Protocol in workflow.md)

## Phase 4: Observability Stack
- [ ] Task: Extend `docker-compose.yml` with order-service/inventory-service + prometheus/grafana/elasticsearch/kibana/filebeat, all under `profiles: ["observability"]`; add Prometheus scrape config + Filebeat docker-log-collection config.
- [ ] Task: Run `docker compose --profile observability up -d`; confirm all 8 services healthy, and plain `docker compose up -d` still starts only Postgres.
- [ ] Task: Add Grafana provisioning (datasource + the dashboard JSON); confirm via Grafana's HTTP API.
- [ ] Task: Confirm Filebeat is shipping both services' container logs into Elasticsearch.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Observability Stack' (Protocol in workflow.md)

## Phase 5: Verification, Docs, README
- [ ] Task: Add `scripts/verify-observability-stack.sh` (brings up the profile, runs the Gatling healthy + degraded passes, checks Prometheus/Grafana/Elasticsearch); run it end-to-end.
- [ ] Task: Document the stack's design in `tech-stack.md`.
- [ ] Task: Add the new "build, run, observe" section to `README.md`.
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Verification, Docs, README' (Protocol in workflow.md)
