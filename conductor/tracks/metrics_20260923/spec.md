# Spec: purerest Metrics (RED + Resilience Signals)

## Overview
Add RED metrics (Rate, Errors, Duration) for purerest's inbound (server) and outbound (client) HTTP paths, plus resilience-specific signals (retry attempts, circuit breaker state transitions), built on otel4s's `Meter[F]` API — consistent with how `purerest.tracing` already exposes otel4s's `Tracer[F]` directly (otel4s is already idiomatic Cats-Effect-native, so no wrapping layer is needed, unlike resilience4j). Metrics are exposed via a Prometheus scrape endpoint on each service.

## Functional Requirements
1. **`purerest.metrics.Metrics`** — mirrors `purerest.tracing.Tracing`'s shape:
   - `Metrics.oteljava[F[_]: Async](serviceName: String, port: Int): Resource[F, Meter[F]]` — wires otel4s's `oteljava` backend to the OTel SDK's Prometheus exporter, exposing `/metrics` on the given port (mirrors `Tracing.console`'s Resource-based setup, swapping the exporter).
   - `Metrics.test[F[_]: Async]: Resource[F, MetricsTestkit]` — otel4s's in-memory metrics testkit, mirroring `Tracing.test`'s `TracesTestkit` pattern, for assertions in automated tests without a real Prometheus server.
2. **`purerest.metrics.ServerMetrics.middleware[F[_]: Async](meter: Meter[F])(routes: HttpRoutes[F]): HttpRoutes[F]`** — analogous to `ServerTracing.middleware`; records a `http.server.request.duration` histogram (seconds) per request, with attributes `http.request.method`, `http.route`, `http.response.status_code` (OTel semantic-convention names).
3. **`purerest.metrics.ClientMetrics.middleware[F[_]: Async](meter: Meter[F])(client: Client[F]): Client[F]`** — analogous to `ClientTracing.middleware`; records `http.client.request.duration` with attributes `http.request.method`, `server.address`, `http.response.status_code`.
4. **Retry attempt metrics**: `Retry.middleware` additionally takes a `Meter[F]` and records a `purerest.retry.attempts` counter (purerest-prefixed — no OTel semantic convention exists for this) with an `outcome` attribute (`retried` | `succeeded` | `exhausted`).
5. **Circuit breaker metrics**: `CircuitBreaker.middleware` additionally takes a `Meter[F]` and records a `purerest.circuit_breaker.state_transitions` counter with `from_state`/`to_state` attributes, plus a `purerest.circuit_breaker.calls_rejected` counter incremented on each fail-fast rejection while open.
6. Wire `ServerMetrics`/`ClientMetrics` into `order-service` and `inventory-service`'s `Main.scala` alongside the existing tracing middleware; wire the `Meter[F]` into `Resilience.middleware`'s construction in `order-service`.
7. Each service exposes its Prometheus scrape endpoint on a configurable port (env var, matching the project's existing ad hoc/PureConfig config style per service).

## Non-Functional Requirements
- No new abstraction/wrapping layer over otel4s's own types — `Meter[F]` is used directly as part of purerest's public API, matching the tracing precedent.
- 100% statement/branch coverage on every file this track adds or changes (project's standing bar).
- scalafmt applied, scoped only to this track's files.

## Acceptance Criteria
- With order-service and inventory-service running, `GET :<port>/metrics` on each returns Prometheus-exposition-format text.
- After placing at least one order, `http_server_request_duration_seconds` and `http_client_request_duration_seconds` series are present with the expected labels.
- After inducing a transient failure (reusing the resilience track's induced-failure mechanism) and a sustained failure, `purerest_retry_attempts_total` and `purerest_circuit_breaker_state_transitions_total`/`purerest_circuit_breaker_calls_rejected_total` series appear and reflect the expected counts.
- A verification script (matching the resilience track's `scripts/verify-*.sh` pattern) demonstrates all of the above end-to-end.

## Out of Scope
- A live/current-state circuit-breaker gauge (only transition + rejection counters this track).
- OTLP/collector push export — Prometheus scrape only.
- Dashboards, alerting, or Grafana provisioning.
- Metrics for order-service's Postgres/Skunk calls — HTTP-layer only.
