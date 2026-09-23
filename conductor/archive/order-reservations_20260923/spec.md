# Spec: Get Orders Showing Reservations

## Overview
Add `GET /orders/{id}` to order-service, returning the order together with the reservation made against it and an order `status` field. Closes the gap left by the persistence track: `POST /orders` calls inventory-service and gets a `reservationId` back, but today that id is never persisted — it's lost after the HTTP response. This track persists the reservation link and exposes it on read.

## Functional Requirements
1. **Schema**: New Flyway migration `V2__add_reservation_to_orders.sql` adding `reservation_id UUID NOT NULL`, `reserved_quantity INT NOT NULL`, `status TEXT NOT NULL DEFAULT 'reserved'` to the `orders` table.
2. **Domain model**: Extend `Order` with `status: String`, `reservationId: String`, `reservedQuantity: Int`, `createdAt: java.time.Instant` (first `java.time` usage in the codebase — circe 0.14's built-in `Instant` codec covers JSON; a Skunk `timestamptz` codec covers the DB read).
3. **OrderStore**: `create` gains `reservationId`/`reservedQuantity` parameters (both `OrderStore.inMemory` and `OrderStore.postgres` implementations updated to keep compiling against the shared trait). New `get(id: String): F[Option[Order]]` method (`OrderStore.postgres` does a real `SELECT`; `OrderStore.inMemory` reads its `Ref` map).
4. **Typed error handling**: Introduce `sealed trait OrderError` with `case object OrderNotFound extends OrderError` — the first modeled domain error in this codebase (every existing endpoint uses `Unit` as its tapir error type today). Wire it through tapir's `errorOut` on the new endpoint, mapped to `404` with a small JSON error body, per `product-guidelines.md`.
5. **Routes**: `OrderRoutes` gains a `GET /orders/{id}` tapir endpoint. `POST /orders`'s existing handler is updated to pass the reservation's id/quantity into `store.create`.
6. **Response model**: `OrderResponse` gains `status`, `createdAt` fields (flat, mirroring the flat DB schema — no nested `reservation` object) and is reused by both `POST /orders` and the new `GET /orders/{id}` — additive only, existing fields (`id`, `item`, `quantity`, `reservationId`) unchanged.

## Non-Functional Requirements
- `GET /orders/{id}` returns the **persisted snapshot** taken at order-creation time — no live call to inventory-service (per the accepted recommendation: inventory-service has no cancel/mutate capability today, so nothing can go stale).
- `POST /orders`'s response change is additive only — no existing field removed or renamed.
- The migration assumes a resettable/dev-only database (this is a personal project with no production data) — no backfill strategy is provided for hypothetical pre-existing rows lacking the new `NOT NULL` columns.

## Acceptance Criteria
- After `POST /orders`, `GET /orders/{id}` returns `200` with the order's `status` (`"reserved"`), its `reservationId`, `reservedQuantity`, and `createdAt`.
- `GET /orders/{unknown-id}` returns `404` with a small JSON error body (not a generic/empty 404).
- Existing `POST /orders` behavior and its pre-existing response fields are unchanged.
- Unit and integration test suites pass; coverage on all new/changed code matches the bar the persistence track established (100% statement/branch on code this track is responsible for).

## Out of Scope
- `GET /orders` (list-all) — no clear consumer/use case yet.
- Reservation expiry/release, payment, fulfillment status — separate backlog items (Payment integration, Reservation expiry/release).
- Any live/cross-service call from the read path.
- Authentication/ownership scoping of orders (product.md non-goal).
