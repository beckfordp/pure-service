# Track: purerest — Tracing + Structured Logging

## Overview
Adds the first cross-cutting concern purerest promises: distributed tracing and trace-correlated structured logging, propagated across the order-service → inventory-service HTTP call proven in the walking-skeleton track. This directly fulfills `product.md`'s Core Use Case description ("a trace/span is created for the inbound request and propagated across the HTTP call to inventory-service... logs on both sides are correlated by trace id") and one bullet of `tech-stack.md`'s Observability section (Tracing, Logging — Metrics remain deferred to a later track).

Library choice: **otel4s** (Typelevel's OpenTelemetry library for Cats Effect, `oteljava` backend) for tracing — chosen over raw `opentelemetry-java` because it's tagless-final/Cats-Effect-native, matching `product-guidelines.md`'s purerest API Style guideline, rather than requiring manual Java interop. **log4cats** for structured logging, with trace/span id injected into log context.

## Functional Requirements
1. **purerest.tracing**: a tracer construction API (`Resource[F, Tracer[F]]`, otel4s-based) with two configured exporters:
   - An **in-memory exporter**, usable from tests to assert on captured spans (trace id continuity, span names, parent/child relationships).
   - A **console exporter**, so spans print to stdout when a service runs — enabling manual verification by watching the terminal while curl-ing a running service (no real collector/backend required this track; OTLP/Jaeger export is deferred).
2. **purerest.tracing server middleware**: wraps an `HttpRoutes[F]`, creating (or continuing, if a trace context is present in inbound headers) a span for each request.
3. **purerest.tracing client middleware**: wraps a `Client[F]` (composing with the existing `purerest.client.HttpClient`), injecting the active span's trace context into outgoing request headers (W3C Trace Context, otel4s's default propagator).
4. **purerest.logging**: a structured/contextual logging API (log4cats-based) that includes the current trace id and span id in every log line emitted within a traced scope, without requiring each call site to pass them explicitly.
5. **Wire into inventory-service**: `InventoryRoutes` uses the server tracing middleware; `Main` constructs the tracer (console + in-memory-capable) and passes it through; reservation handling logs via the trace-correlated logger.
6. **Wire into order-service**: `OrderRoutes`'/`InventoryClient`'s outbound call uses the client tracing middleware, so the span created for the inbound `POST /orders` request propagates to the outbound call to inventory-service; order handling logs via the trace-correlated logger.

## Non-Functional Requirements
- No annotations, reflection, or macros for wiring tracing/logging in — consistent with `product-guidelines.md`.
- purerest's tracing/logging APIs are generic (`F[_]`), reusable by any future purerest consumer, not order-service/inventory-service-specific.
- Adding tracing must not change any existing endpoint's request/response JSON shape or status codes (backward compatible with the walking-skeleton's routes).

## Acceptance Criteria
1. An automated test (using otel4s's in-memory exporter) proves: a span is created for an inbound `POST /orders` request, a child span is created for the outbound call to inventory-service, and both spans share the same trace id.
2. Running inventory-service and order-service locally with the console exporter enabled, `curl -X POST http://localhost:8080/orders -d '{"item":"widget","quantity":1}'` prints spans to both services' terminals, visibly sharing a trace id.
3. Log lines emitted during a traced request (both services) include the request's trace id and span id.
4. `sbt test` passes across all modules; existing walking-skeleton tests continue to pass unmodified (response shapes/status codes unchanged).
5. `purerest.tracing`'s server/client middleware and `purerest.logging`'s contextual logger have no order-service/inventory-service-specific code — proven by both services depending on them the same way.

## Out of Scope
- Metrics (Prometheus) — separate, later track.
- Real OTLP/Jaeger/Zipkin collector export — console + in-memory only this track.
- Resilience (retry, circuit breaker) — separate, later track (spec explicitly deferred this in the walking-skeleton track too).
- PostgreSQL persistence — unrelated, separate track.
