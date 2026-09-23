# Plan: Add a runtime-adjustable induced-failure endpoint to inventory-service

## Phase 1: Runtime-Adjustable Induced-Failure Config
- [ ] Task: Write failing tests (Red) for `GET /admin/induced-failure` returning the current config, backed by a `Ref[F, InducedFailureConfig]` seeded with a known value.
- [ ] Task: Change `InventoryRoutes` to hold induced-failure config in a `Ref[F, InducedFailureConfig]` instead of a plain value (`serverEndpoint`/`routes` now take the Ref; `/inventory/reserve` reads it fresh per request); add the `GET /admin/induced-failure` tapir endpoint. Update existing tests to construct via `Ref.of(...)`. Implement to pass (Green).
- [ ] Task: Write failing tests (Red) for `PATCH /admin/induced-failure` with a valid body updating the Ref, and a subsequent `GET` reflecting the new values.
- [ ] Task: Implement the `PATCH` endpoint (Green).
- [ ] Task: Write failing tests (Red) asserting `PATCH` rejects `failureRate` outside `[0.0, 1.0]` and negative `delayMs` with 400 + a typed JSON error body, leaving the live config unchanged.
- [ ] Task: Model the validation error as a typed domain error (per `product-guidelines.md`) and wire it to a 400 response. Implement to pass (Green).
- [ ] Task: Write a failing integration-style test (Red): GET baseline -> PATCH `failureRate=1.0` -> `POST /inventory/reserve` fails (500) -> PATCH `failureRate=0.0` -> `POST /inventory/reserve` succeeds — proving the change takes effect live without a restart.
- [ ] Task: Make this test pass (Green), fixing anything needed for the live read-through to work end-to-end.
- [ ] Task: Wire `Main.scala` to construct the Ref at startup (seeded from the existing `INVENTORY_INDUCED_FAILURE_RATE`/`_DELAY_MS` env vars, unchanged default behavior) and register both new endpoints alongside `/inventory/reserve` in `Docs.routes`.
- [ ] Task: Run `sbt coverage inventoryService/test coverageReport`; confirm high coverage on changed files and the full suite green.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Runtime-Adjustable Induced-Failure Config' (Protocol in workflow.md) — start inventory-service, curl GET/PATCH/GET/POST-reserve to confirm live behavior, and confirm both new endpoints appear in inventory-service's Swagger UI at `/docs`.
