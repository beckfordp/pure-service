# Track: Walking Skeleton

## Goal
Stand up the multi-module sbt build (`purerest`, `order-service`, `inventory-service`) and prove out a real, end-to-end call path — `order-service` calling `inventory-service` over HTTP via a purerest-provided client — before any cross-cutting concerns (tracing, resilience, observability) are added. This is the foundation later tracks build on.

## In Scope
- Convert the single-module sbt build into a multi-module build with three subprojects: `purerest`, `orderService`, `inventoryService`.
- `purerest`: a minimal HTTP client wrapper around http4s's Ember client (`Resource[F, Client[F]]`), exposed as a tagless-final API. This is the seed of the library — later tracks add tracing/retry/circuit-breaker to this same client construction point.
- `inventory-service`: an http4s server exposing `POST /inventory/reserve` (circe JSON body: `{ "item": String, "quantity": Int }`), returning `201 Created` with a reservation id. Backed by an in-memory store (no Postgres yet).
- `order-service`: an http4s server exposing `POST /orders` (circe JSON body: `{ "item": String, "quantity": Int }`). On receipt, calls `inventory-service`'s reserve endpoint using purerest's client, then returns `201 Created` with an order id. Backed by an in-memory store (no Postgres yet).
- Unit tests for purerest's client construction, and for both services' request/response handling (using http4s's test/client utilities — no real network sockets required).
- An integration-style test proving the full `order-service` → `inventory-service` call path works (e.g. running inventory-service's routes in-process and pointing order-service's client at it).

## Explicitly Deferred (not a tech-stack deviation — scoped to a later track)
- PostgreSQL persistence for orders/inventory (in-memory stores stand in for this track only).
- Tracing (OpenTelemetry), structured logging (log4cats), metrics (Prometheus).
- Resilience: retry policies and circuit breaker.
- Typed domain error ADTs with full HTTP error mapping (basic success/4xx handling only for now).

## Out of Scope
- Authentication/authorization
- Deployment/orchestration
- UI/frontend

## Acceptance Criteria
1. `sbt compile` succeeds across all three modules.
2. `sbt test` passes for all three modules.
3. Starting `inventory-service` and `order-service` locally, `curl -X POST http://localhost:<order-port>/orders -d '{"item":"widget","quantity":1}'` returns `201 Created` with an order id, and a corresponding reservation was made in inventory-service.
4. purerest's client wrapper has no dependency on order-service or inventory-service specifics — it is generic and reusable (proven by both services depending on it the same way).
