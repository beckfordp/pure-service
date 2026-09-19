# Implementation Plan: Walking Skeleton

## Phase 1: Multi-Module Build Scaffolding
- [x] Task: Restructure `build.sbt` into an sbt multi-module build with subprojects `purerest` (`modules/purerest`), `orderService` (`modules/order-service`), `inventoryService` (`modules/inventory-service`); add shared dependency versions (Cats Effect 3, http4s, circe, munit) as `val`s at the root. [2a7b018]
- [x] Task: Move/replace the scaffold's `src/main/scala/Main.scala` and `src/test/scala/MySuite.scala` — remove the g8 template stub, add a placeholder `Main` per module compiling against munit. [704716b]
- [x] Task: Conductor - User Manual Verification 'Multi-Module Build Scaffolding' (Protocol in workflow.md)

## Phase 2: purerest — Minimal HTTP Client
- [ ] Task: Add http4s (client, ember-client, circe) and circe dependencies to the `purerest` module.
- [ ] Task: Implement `purerest.client.HttpClient` — a tagless-final `Resource[F[_]: Async, Client[F]]` wrapper around http4s's `EmberClientBuilder`, with unit tests verifying the resource can be acquired and used against a stub route.
- [ ] Task: Conductor - User Manual Verification 'purerest — Minimal HTTP Client' (Protocol in workflow.md)

## Phase 3: inventory-service
- [ ] Task: Add http4s (server, ember-server, dsl, circe) dependencies to `inventoryService`; depend on `purerest`.
- [ ] Task: Implement an in-memory `InventoryStore` (reserve stock, return a reservation id) with unit tests.
- [ ] Task: Implement `POST /inventory/reserve` http4s route (circe JSON in/out) wired to `InventoryStore`, with unit tests using http4s's `Request`/route-under-test pattern (no real server socket).
- [ ] Task: Wire an Ember server `Main` entrypoint for `inventory-service`.
- [ ] Task: Conductor - User Manual Verification 'inventory-service' (Protocol in workflow.md)

## Phase 4: order-service
- [ ] Task: Add http4s (server, ember-server, dsl, circe) dependencies to `orderService`; depend on `purerest`.
- [ ] Task: Implement an in-memory `OrderStore` (create order, return an order id) with unit tests.
- [ ] Task: Implement `POST /orders` http4s route (circe JSON in/out) that calls inventory-service's reserve endpoint via purerest's `HttpClient`, then persists the order via `OrderStore`, with unit tests stubbing the inventory-service call.
- [ ] Task: Wire an Ember server `Main` entrypoint for `order-service` (configurable inventory-service base URL).
- [ ] Task: Add an integration-style test that runs inventory-service's routes in-process and exercises order-service's `POST /orders` handler end-to-end against it, proving the full call path.
- [ ] Task: Conductor - User Manual Verification 'order-service' (Protocol in workflow.md)
