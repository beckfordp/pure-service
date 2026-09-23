# Plan: purerest Metrics (RED + Resilience Signals)

## Phase 1: Metrics Foundation [checkpoint: ff0a951]
- [x] Task: Add `opentelemetry-exporter-prometheus` dependency to `purerest`; verify it resolves via `sbt purerest/update` (adjust version/coordinates based on the real resolution, matching prior tracks' dependency-verification pattern). Document the choice in `tech-stack.md`. [244c4b6]
- [x] Task: Write a failing test asserting `Metrics.test` (otel4s in-memory `MetricsTestkit`) records a manually-emitted counter value (Red). [a868bd9]
- [x] Task: Implement `purerest.metrics.Metrics.oteljava` (Prometheus-exporter-backed `Meter[F]` `Resource`, port-configurable) and `Metrics.test` (in-memory testkit `Resource`), mirroring `purerest.tracing.Tracing`'s shape (Green). [a868bd9]
- [x] Task: Conductor - User Manual Verification 'Phase 1: Metrics Foundation' (Protocol in workflow.md) — no wiring into a running service yet (that's Phase 4), so verification is the automated test suite itself: full purerest/test green (25/25), including MetricsSuite's manually-emitted-counter scenario against the in-memory testkit. [ff0a951]

## Phase 2: Server & Client RED Metrics [checkpoint: 5c2dc8f]
- [x] Task: Write failing tests against a stub `HttpRoutes[F]`/`Client[F]` and `Metrics.test`'s testkit: a handled request records a `http.server.request.duration` / `http.client.request.duration` measurement with the expected method/route-or-address/status-code attributes (Red). [ba9d9a6]
- [x] Task: Implement `ServerMetrics.middleware` and `ClientMetrics.middleware` in `purerest.metrics` (Green). [ba9d9a6]
- [x] Task: Conductor - User Manual Verification 'Phase 2: Server & Client RED Metrics' (Protocol in workflow.md) — no wiring into a running service yet (Phase 4); verification is the automated test suite: full purerest/test green (28/28), including ServerMetricsSuite/ClientMetricsSuite's histogram-with-attributes scenarios. [5c2dc8f]

## Phase 3: Resilience Metrics [checkpoint: 04c8a0d]
- [x] Task: Write failing tests: a retried call records `purerest.retry.attempts` with the right `outcome` attribute across retried/succeeded/exhausted scenarios; a circuit breaker open/close/reject records `purerest.circuit_breaker.state_transitions` / `purerest.circuit_breaker.calls_rejected` (Red). [46214fe]
- [x] Task: Extend `Retry.middleware` and `CircuitBreaker.middleware` to accept a `Meter[F]` and emit these counters; update `Resilience.middleware`'s signature accordingly (Green). [46214fe]
- [x] Task: Conductor - User Manual Verification 'Phase 3: Resilience Metrics' (Protocol in workflow.md) — order-service/inventory-service wiring is still Meter.noop (Phase 4's job); verification is the automated test suite across all modules: purerest 35/35 (including RetryMetricsSuite/CircuitBreakerMetricsSuite), inventoryService and orderService both green (21/21), plus a separate compile check confirming order-service/inventory-service build cleanly after the signature change. [04c8a0d]

## Phase 4: Wire into Services [checkpoint: ed4f7c9]
- [x] Task: Wire `Metrics.oteljava` + `ServerMetrics.middleware`/`ClientMetrics.middleware` into `order-service` and `inventory-service`'s `Main.scala`, alongside existing tracing; wire the `Meter[F]` into `Resilience.middleware`'s construction in `order-service`. [dbf9d6e]
- [x] Task: Add a configurable scrape port (env var) per service, following the project's existing ad hoc/PureConfig config style per service. [dbf9d6e]
- [x] Task: Conductor - User Manual Verification 'Phase 4: Wire into Services' (Protocol in workflow.md) — added `scripts/verify-metrics-wiring.sh` (docker-compose + both services, confirms `GET :<port>/metrics` returns Prometheus text with http_server_request_duration_seconds/http_client_request_duration_seconds series on both after a healthy `POST /orders`) and ran it directly; all checks passed. [ed4f7c9]

## Phase 5: End-to-End Verification & Cleanup
- [x] Task: Add `scripts/verify-metrics-end-to-end.sh`: reuse the resilience track's induced-failure mechanism to trigger retries and a circuit-breaker trip, then assert the expected `purerest_retry_attempts_total` / `purerest_circuit_breaker_state_transitions_total` / `purerest_circuit_breaker_calls_rejected_total` series and values appear in `/metrics` output. [d3b5ba3]
- [x] Task: Run `sbt coverage purerest/test inventoryService/test orderService/test coverageReport`; confirm 100% statement/branch coverage on every file this track adds or changes; close any gaps found. Found and closed one real gap (Metrics.oteljava's real Prometheus-exporter path was only exercised manually, not by `sbt test`). [a8c32b9]
- [ ] Task: Run `scalafmtOnly` scoped to this track's own files.
- [ ] Task: Conductor - User Manual Verification 'Phase 5: End-to-End Verification & Cleanup' (Protocol in workflow.md)
