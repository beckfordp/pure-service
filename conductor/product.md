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

## Customer Journey (Vision)
The Core Use Case above is the technical design driver (why purerest's cross-cutting
concerns exist). This section is the north-star customer narrative behind it — the
full order lifecycle a real buyer would experience, most of which is not built yet.
It exists to give future tracks context on where a given slice of work sits in the
bigger picture, not as a commitment to build all of it.

1. **Browse & decide** — customer picks an item + quantity. *(No catalog exists —
   likely always out of scope for this project unless a `catalog-service` shows up.)*
2. **Place order** — `POST /orders {item, quantity}`. order-service reserves stock on
   inventory-service synchronously, persists the order, returns the order + a
   reservation id. *(Built.)*
3. **Reservation held** — inventory-service holds stock against that reservation. No
   expiry, no release, no cancel exists — once reserved, reserved forever. *(Built, but
   minimal — nothing frees stock if the customer never pays; see backlog.)*
4. **Payment** — customer pays for the reserved order. *(Not built — no
   payment-service, no payment status anywhere yet.)*
5. **Fulfillment** — reserved stock ships, order marked fulfilled. *(Not built.)*
6. **Customer checks status** — `GET /orders/{id}` shows the order, its reservation,
   and (eventually) payment/fulfillment status. *(Built — returns the order's
   `status` (currently always `"reserved"`), `reservationId`, `reservedQuantity`, and
   `createdAt`; `status`'s value set is expected to grow once payment/fulfillment
   exist. A 404 with a JSON error body is returned for an unknown id — the first
   modeled domain error in the codebase, per `product-guidelines.md`.)*

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
