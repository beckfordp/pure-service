# Product Guide

## Vision
`pure-service` is the home of **purerest** — a pure-functional-programming platform library (Scala 3, Cats Effect, http4s) that provides the cross-cutting concerns every production microservice needs — tracing, observability, structured logging, and resilience (retry + circuit breaker) — as composable, annotation-free building blocks. Two reference services, **order-service** and **inventory-service**, are built on top of purerest to drive its design and prove it out end-to-end.

## Target Users
- Internal engineering (primarily the author) — a personal platform-engineering project and learning vehicle for pure FP in Scala 3.
- Any future team/service that would adopt purerest as its microservice foundation.

## Core Use Case
`order-service` receives `POST /orders` requests. To fulfill an order it must reserve stock, so it calls `inventory-service` over HTTP using purerest's resilient client. This single call path is the design driver for purerest:
- **Tracing**: a trace/span is created for the inbound request and propagated across the HTTP call to inventory-service.
- **Structured logging**: logs on both sides are correlated by trace id.
- **Metrics**: request/latency/error metrics are emitted for both the inbound and outbound call.
- **Resilience**: the client retries transient failures and trips a circuit breaker when inventory-service is slow or failing, without either service needing annotations — behavior is composed via purerest's API.

`inventory-service` is a second, independently-built purerest consumer (not inventory-service-specific), so it can also be made deliberately slow/flaky to exercise purerest's resilience features.

## Components
1. **purerest** (library) — server + client toolkit built on Cats Effect 3 and http4s:
   - Tracing middleware/propagation (OpenTelemetry)
   - Structured, contextual logging (log4cats)
   - Metrics (Prometheus-compatible)
   - Resilient HTTP client: retry policies + circuit breaker, composed via combinators — no annotations
   - Self-documenting API endpoints (tapir): each endpoint is described once and interpreted into
     both real http4s routes and a generated, always-in-sync OpenAPI spec + browsable Swagger UI
2. **order-service** — REST API (http4s) backed by PostgreSQL via Skunk; orchestrates order placement, calling inventory-service via purerest's client.
3. **inventory-service** — REST API (http4s) exposing stock reservation endpoints; second reference consumer of purerest, used to validate resilience behavior under induced failure/latency.

## Key Features (initial track scope)
- purerest: tracing, structured logging, metrics, retry, circuit breaker — as library building blocks
- order-service: `POST /orders` endpoint, order persistence, resilient call to inventory-service
- inventory-service: stock reservation endpoint(s), reference consumer of purerest

## Non-Goals (for now)
- Authentication/authorization
- Multi-service deployment/orchestration (Kubernetes, etc.) — local/dev focus first
- UI/frontend

## Assumptions to confirm
- Resilience (retry + circuit breaker) will be built on Cats Effect primitives, optionally leaning on an existing library (e.g. cats-retry) rather than reinventing everything from scratch — open to reconsidering during implementation.
