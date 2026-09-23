# Plan: Get Orders Showing Reservations

## Phase 1: Schema & Domain Model [checkpoint: 31e0c6f]
- [x] Task: Write a failing test asserting the `orders` table has `reservation_id`, `reserved_quantity`, `status` columns after migration (Testcontainers Postgres, extends the pattern from `MigrationsSuite`) (Red). [bb85c9f]
- [x] Task: Add `V2__add_reservation_to_orders.sql`; extend `Order` with `status`, `reservationId`, `reservedQuantity`, `createdAt: java.time.Instant` (Green). Necessarily also updated `OrderStore.create`'s signature and added `get(id)` for both implementations, since the compiler forces this — see Phase 2 note. [7a5f61d]
- [x] Task: Conductor - User Manual Verification 'Phase 1: Schema & Domain Model' (Protocol in workflow.md) — added `scripts/verify-order-reservations-schema.sh` (throwaway Postgres, boots order-service, confirms both migrations apply and the orders table has the expected columns) and ran it directly; all checks passed. [31e0c6f]

## Phase 2: OrderStore — create() and get()
- [x] Task: Write failing tests (real Testcontainers Postgres, per the persistence track's established pattern) for `OrderStore.postgres.create` now accepting `reservationId`/`reservedQuantity` and persisting them, and for a new `get(id)` returning `Some(order)` for an existing id and `None` for an unknown one (Red). Genuinely Red — surfaced a real Skunk varchar/text column-type mismatch bug in `get()`'s query (fixed in the next task). [0493d9b]
- [x] Task: Update `OrderStore` trait + both `inMemory` and `postgres` implementations: `create` takes the new params, `get` does a real `SELECT` (postgres) / `Ref` lookup (inMemory) (Green). Implementation itself landed in Phase 1 (compiler-forced coupling); this task's remaining work was fixing the varchar/text codec bug the new tests surfaced. [e625dfb]
- [x] Task: Conductor - User Manual Verification 'Phase 2: OrderStore — create() and get()' (Protocol in workflow.md) — no new HTTP-visible behavior this phase (OrderStore is internal; the GET route lands in Phase 3), so verification is the automated test suite itself: full orderService/test green (19/19), including the new reservation/get() assertions against a real Postgres. [pending]

## Phase 3: Typed Error + GET /orders/{id} Route
- [ ] Task: Write a failing test for the new `GET /orders/{id}` tapir endpoint returning 404 with a JSON error body for an unknown id (Red).
- [ ] Task: Define `sealed trait OrderError` / `case object OrderNotFound`; add the `GET /orders/{id}` endpoint to `OrderRoutes` with `errorOut` mapping `OrderNotFound` to 404; update `POST /orders`'s handler to pass reservation id/quantity into `store.create`; extend `OrderResponse` with `status`/`createdAt` (Green).
- [ ] Task: Write a failing end-to-end integration test: `POST /orders` then `GET /orders/{id}` returns 200 with the persisted order+reservation+status (Red — expected to fail until the full path is wired).
- [ ] Task: Wire everything together so the end-to-end test passes (Green).
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Typed Error + GET /orders/{id} Route' (Protocol in workflow.md)

## Phase 4: Coverage & Cleanup
- [ ] Task: Run `sbt coverage orderService/test orderService/coverageReport`; confirm 100% statement/branch coverage on every file this track adds or changes (the bar the persistence track established).
- [ ] Task: Run `sbt orderService/scalafmtCheck` (scoped to this track's own files, matching the persistence track's precedent of not reformatting the wider pre-existing codebase).
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Coverage & Cleanup' (Protocol in workflow.md)
