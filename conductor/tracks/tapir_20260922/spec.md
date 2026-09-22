# Track: Adopt tapir for endpoint definitions

## Overview

Adopt tapir as the endpoint-description layer for `inventory-service` and `order-service`, so the
OpenAPI/Swagger documentation is generated from the same source of truth as the http4s routes
themselves, rather than hand-maintained separately or absent — as discussed in
`docs/functional-design-patterns-scala-haskell-category-theory.md`'s "Generating OpenAPI/Swagger
docs: http4s vs. tapir" section.

## Functional Requirements

- Convert `InventoryRoutes` (`POST /inventory/reserve`) from `HttpRoutes.of[F] { case ... }`
  pattern matching to a tapir `Endpoint` value, interpreted to `HttpRoutes[F]` via
  `tapir-http4s-server`.
- Convert `OrderRoutes` (`POST /orders`) the same way.
- Add a shared `purerest` helper that takes a service's tapir endpoints and produces both the
  interpreted `HttpRoutes[F]` and a mounted Swagger UI route, so both services reuse one wiring
  path instead of duplicating `tapir-http4s-server`/`tapir-swagger-ui-bundle` setup.
- Wire the helper into both services' `Main`, exposing Swagger UI at a documented path.
- Preserve existing request/response schemas (`CreateOrderRequest`/`OrderResponse`, `Reservation`,
  `ReservationView`) — tapir schema derivation must line up with the current circe `Codec`s.

## Non-Functional Requirements

- No change to the existing HTTP contract (paths, methods, status codes, JSON shapes) for either
  service — this is an internal implementation swap, not an API redesign.
- OpenAPI spec built once at startup, not per-request.

## Acceptance Criteria

- Existing test suites (including the order-service ↔ inventory-service trace-continuity test)
  pass unchanged against the tapir-interpreted routes.
- Both services serve a browsable Swagger UI for their converted endpoint(s).
- The generated OpenAPI yaml/json is reachable as its own route, not only through the UI.
- Manual verification: confirm Swagger UI renders correctly for both services.

## Out of Scope

- Converting purerest's own tracing/client code (no HTTP-facing routes to document there).
- Authentication/authorization on the docs/swagger routes.
- Client-code generation from the OpenAPI spec (tapir also supports this — deferred).
- Any change to `InventoryStore`/`OrderStore` business logic.
