# Plan: Drive a Gatling scenario that ramps inventory-service's induced-failure rate, and add Grafana panels validating resilience effectiveness

## Phase 1: Gatling ramping scenario
- [ ] Task: Add a second scenario to `OrderPlacementSimulation.scala` (`rampFailureRate`) that issues `PATCH /admin/induced-failure` requests against a configurable `inventoryBaseUrl` (`-D` property, `localhost:8081` default) on a timed schedule: `0.0` -> `0.6` -> `0.0`, each phase comfortably longer than order-service's 30s `resetTimeout`.
- [ ] Task: Wire both scenarios into one `setUp(...)` so they run concurrently; run `sbt loadTest/Gatling/test` against a running `--profile observability` stack and confirm via Prometheus's API that `purerest_circuit_breaker_state` transitions CLOSED -> OPEN -> CLOSED within the run, calibrating the `0.6` rate/phase durations empirically if the trip isn't reliable (same approach as the prior track's 0.3->0.5 calibration).
- [ ] Task: Confirm the existing order-placement scenario's own RED metrics still populate normally with both scenarios running concurrently (no interference between them).

## Phase 2: Recover and update verify-observability-stack.sh
- [ ] Task: Recover `scripts/archive/verify-observability-stack.sh` back to `scripts/` (`git mv`); update README.md/tech-stack.md references back to the un-archived path.
- [ ] Task: Replace the script's two-pass structure (healthy pass, then container-restarted degraded pass) with a single run of the updated Gatling simulation; adapt the retry/circuit-breaker assertions to check for a full transition cycle (OPEN and back to CLOSED) rather than just "some activity."
- [ ] Task: Run the updated script end-to-end; confirm all existing assertions (Prometheus targets, Grafana provisioning, Kibana Data View, Elasticsearch log indexing) still pass with the new single-run structure.

## Phase 3: Grafana panels
- [ ] Task: Add "Circuit Breaker: State Timeline" (`state-timeline` panel plotting `purerest_circuit_breaker_state`) to `observability/grafana/provisioning/dashboards/purerest.json`.
- [ ] Task: Add "Retry Success Rate" (`timeseries` panel, successful-including-retried vs exhausted ratio from `purerest_retry_attempts_total`) to the same dashboard.
- [ ] Task: Extend `verify-observability-stack.sh` with assertions that both new panels' PromQL queries (via Grafana's datasource proxy, matching the existing dashboard-query pattern) resolve non-empty data after the ramping run.
- [ ] Task: Run the fully-updated script end-to-end once more; confirm all checks (existing + new) pass.
- [ ] Task: Update tech-stack.md's Load Testing / Local Observability Stack sections to document the new scenario, the recovered script, and the two new panels.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Grafana panels' (Protocol in workflow.md) — bring up the stack, run the recovered script, open Grafana in a browser, and visually confirm both new panels show a real trip-and-recovery cycle from the run.
