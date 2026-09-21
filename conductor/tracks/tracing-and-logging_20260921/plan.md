# Implementation Plan: purerest — Tracing + Structured Logging

## Phase 1: purerest.tracing — Tracer Construction [checkpoint: 2720237]
- [x] Task: Add otel4s (core, oteljava, oteljava-testkit, exporter-console — exact artifacts confirmed at implementation time) dependencies to the `purerest` module. [769e13e]
- [x] Task: Implement `purerest.tracing.Tracing` — `Resource[F, Tracer[F]]` constructors for (a) console-exporting tracers (runtime/manual use) and (b) in-memory-exporting tracers exposing captured spans (test use), with unit tests verifying a created span is recorded by the in-memory exporter. [cb345dc]
- [x] Task: Conductor - User Manual Verification 'purerest.tracing — Tracer Construction' (Protocol in workflow.md)

## Phase 2: purerest.tracing — Server + Client Middleware
- [x] Task: Implement a server tracing middleware wrapping `HttpRoutes[F]` — creates (or continues, if a W3C trace context is present in inbound headers) a span per request, with unit tests (in-memory exporter) verifying a span is recorded when a request is run through the wrapped routes. [40d4ef2]
- [x] Task: Implement a client tracing middleware wrapping `Client[F]` (composing with `purerest.client.HttpClient`) — injects the active span's trace context into outgoing request headers, with unit tests verifying the propagation header appears on outgoing requests (e.g. a stub route capturing received headers). [a9b12d0]
- [x] Task: Conductor - User Manual Verification 'purerest.tracing — Server + Client Middleware' (Protocol in workflow.md)

## Phase 3: purerest.logging — Trace-Correlated Structured Logging
- [ ] Task: Add log4cats dependencies to the `purerest` module.
- [ ] Task: Implement `purerest.logging` — a contextual logger that includes the current trace id and span id in every log line when used within a traced scope, with unit tests verifying log output includes the correct ids (log4cats's testing utilities, or an equivalent captured-output approach).
- [ ] Task: Conductor - User Manual Verification 'purerest.logging — Trace-Correlated Structured Logging' (Protocol in workflow.md)

## Phase 4: Wire into inventory-service
- [ ] Task: Update `InventoryRoutes`/`Main` to wrap routes with purerest's server tracing middleware and use the trace-correlated logger when handling `POST /inventory/reserve`; existing `InventoryRoutesSuite` tests must continue to pass unmodified (response shape/status codes unchanged), plus a new test verifying a span is recorded when the wrapped routes are exercised.
- [ ] Task: Conductor - User Manual Verification 'Wire into inventory-service' (Protocol in workflow.md)

## Phase 5: Wire into order-service + Prove End-to-End Trace Continuity
- [ ] Task: Update `order-service`'s `Main`/`InventoryClient` to use purerest's client tracing middleware for the outbound call to inventory-service, and wrap `OrderRoutes` with the server tracing middleware for the inbound `POST /orders`; use the trace-correlated logger when handling the request. Existing `OrderRoutesSuite` tests must continue to pass unmodified.
- [ ] Task: Add an integration test (extending `OrderServiceIntegrationSuite`'s real-inventory-service-on-ephemeral-port pattern) using the in-memory exporter to assert the inbound order-service span and the outbound inventory-service child span share the same trace id — this is the track's Acceptance Criteria #1. Use otel4s's `TraceExpectation`/`TraceForestExpectation`/`SpanExpectation` matcher (`TraceExpectations.check`) for this assertion, per the otel4s docs' recommended testkit pattern for verifying parent-child span hierarchies — not raw `SpanData` field comparisons.
- [ ] Task: Conductor - User Manual Verification 'Wire into order-service + Prove End-to-End Trace Continuity' (Protocol in workflow.md)
