# Implementation Plan: Adopt tapir for endpoint definitions

## Phase 1: Tech-stack update + shared purerest tapir wiring [checkpoint: 648ba76]

- [x] Task: Document tapir as a new tech-stack dependency in `tech-stack.md` (tapir-core,
      tapir-http4s-server, tapir-json-circe, tapir-openapi-docs, tapir-swagger-ui-bundle) before
      any code changes 7908b9d
- [x] Task: Write failing test for a `purerest.docs` helper that, given a list of tapir
      `ServerEndpoint[F, Any]`, produces an `HttpRoutes[F]` serving those endpoints plus mounted
      OpenAPI-yaml/json and Swagger-UI routes a256870
- [x] Task: Implement `purerest.docs` helper to pass the test (interpret endpoints via
      tapir-http4s-server; generate the OpenAPI doc via tapir-openapi-docs; mount via
      tapir-swagger-ui-bundle) a256870
- [x] Task: Conductor - User Manual Verification 'Phase 1' (Protocol in workflow.md) 648ba76

## Phase 2: Convert inventory-service to tapir [checkpoint: ef1fa63]

- [x] Task: Write failing test asserting `POST /inventory/reserve` behaves identically (same
      request/response shape, status codes) when served via the new tapir-based routes 469b186
- [x] Task: Define the `reserve` tapir `Endpoint` (path, JSON request/response schemas derived
      from existing circe `Codec`s, error mapping) 469b186
- [x] Task: Reimplement `InventoryRoutes` using the endpoint + `purerest.docs` helper, replacing
      the `HttpRoutes.of[F]` pattern match; existing tests pass unchanged 469b186
- [x] Task: Wire into `inventory-service`'s `Main`, exposing Swagger UI + OpenAPI route 2374c51
- [x] Task: Conductor - User Manual Verification 'Phase 2' (Protocol in workflow.md) ef1fa63

## Phase 3: Convert order-service to tapir

- [ ] Task: Write failing test asserting `POST /orders` behaves identically via the new
      tapir-based routes (including the order→inventory HTTP call and trace continuity)
- [ ] Task: Define the `createOrder` tapir `Endpoint` (request/response schemas from existing
      circe `Codec`s)
- [ ] Task: Reimplement `OrderRoutes` using the endpoint + `purerest.docs` helper
- [ ] Task: Wire into `order-service`'s `Main`, exposing Swagger UI + OpenAPI route
- [ ] Task: Conductor - User Manual Verification 'Phase 3' (Protocol in workflow.md)
