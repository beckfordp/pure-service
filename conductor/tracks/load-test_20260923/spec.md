# Spec: Add a Gatling-based load-test module

## Overview
Add a Gatling load-test module that repeatedly exercises order-service's `POST /orders` call path (order-service → inventory-service via purerest's resilient client) under sustained concurrent load, generating realistic RED metrics (request rate/duration/errors) and, under induced failure, realistic resilience metrics (retry attempts, circuit-breaker transitions) — exactly the kind of traffic the metrics track's dashboards/series are meant to reflect, but which manual curl-based verification scripts can't produce.

## Functional Requirements
1. Add `modules/load-test` as a new sbt subproject in the aggregate `build.sbt`, with `enablePlugins(GatlingPlugin)`. Add `addSbtPlugin("io.gatling" % "gatling-sbt" % "4.13.3")` to `project/plugins.sbt`, and `"io.gatling.highcharts" % "gatling-charts-highcharts" % "3.15.1" % Test` + `"io.gatling" % "gatling-test-framework" % "3.15.1" % Test` to the module's dependencies.
2. Add one Gatling `Simulation` (`OrderPlacementSimulation`) that repeatedly issues `POST /orders` (`{"item": "widget", "quantity": 1}`) against a configurable base URL (default `http://localhost:8080`), injected as: ramp to ~15 concurrent users over 30s, then ~5 users/sec for a further 60s (~90s total).
3. Add `scripts/loadtest-purerest.sh`: starts docker-compose + both services with `INVENTORY_INDUCED_FAILURE_RATE=0`, runs the simulation, prints a summary of order-service's `/metrics` RED series afterward, tears down; then restarts both services with a moderate `INVENTORY_INDUCED_FAILURE_RATE` (e.g. `0.3`), reruns the same simulation, prints a summary of `purerest_retry_attempts_total`/`purerest_circuit_breaker_*` afterward, tears down.
4. Document the new module and script in `tech-stack.md`.

## Non-Functional Requirements
- Gatling simulations live under the plugin's dedicated `Gatling` sbt configuration, not the default `Test` config — `sbt test`/`sbt compile` at the repo root must **not** execute a ~90s load test as part of the normal fast dev/test loop; only `sbt load-test/Gatling/test` or the script runs it.
- No changes to purerest/order-service/inventory-service production code.
- Full existing test suite (purerest/order-service/inventory-service) stays green.

## Acceptance Criteria
- `scripts/loadtest-purerest.sh` completes both passes successfully, reporting non-zero request counts in the healthy pass and non-zero `purerest_retry_attempts_total`/circuit-breaker counts in the degraded pass.
- `sbt projects`/`sbt test` at the repo root includes `load-test` as a project but does not run its Gatling simulations by default.
- `tech-stack.md` documents the module and script.

## Out of Scope
- CI integration.
- Any change to purerest/order-service/inventory-service production code.
- Publishing or archiving Gatling's generated HTML reports (written locally to `modules/load-test/target/gatling/`, git-ignored).
