# Spec: Order Persistence (Skunk + PostgreSQL)

## Overview
Replace `order-service`'s in-memory `OrderStore` (`Ref[F, Map[String, Order]]`) with a PostgreSQL-backed implementation using Skunk. Schema is versioned via Flyway migrations. Local/dev Postgres runs via Docker Compose; integration tests run against a real Postgres via Testcontainers; unit-level tests use a mocked `OrderStore`/`Session`. Database connection settings (and other existing env-var-based settings currently read ad hoc in `Main.scala`) move to `application.conf`, loaded via PureConfig.

## Functional Requirements
1. **Schema & migrations**: Add a Flyway migration creating an `orders` table: `id UUID PRIMARY KEY`, `item TEXT NOT NULL`, `quantity INT NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`. Flyway runs automatically on `order-service` startup, before the HTTP server binds.
2. **Skunk-backed `OrderStore`**: New implementation of the existing `OrderStore[F]` trait (`OrderStore.postgres` or similar) backed by a Skunk `Session`/`Resource`-pooled session, replacing `OrderStore.inMemory` in `Main.scala`. Preserves the existing `create(item, quantity): F[Order]` signature and `Order(id, item, quantity)` shape (no new fields beyond `created_at` at the persistence layer).
3. **Configuration via PureConfig**: Introduce an `application.conf` (+ per-env overrides if useful) read via PureConfig at startup, covering: Postgres connection (host/port/db/user/password), and the existing `ORDER_SERVICE_PORT` / `INVENTORY_SERVICE_BASE_URL` settings currently read directly from `sys.env`. Config case classes replace the ad hoc `sys.env.get(...)` calls in `Main.scala`.
4. **Local dev environment**: `docker-compose.yml` (or an addition to an existing one, if present) provisioning a Postgres container for `sbt "order-service/run"` and manual testing.
5. **Testcontainers integration tests**: Extend/replace `OrderServiceIntegrationSuite` (and/or add a new suite) to run `POST /orders` end-to-end against a real, ephemeral Postgres container, verifying the row is persisted correctly.
6. **Unit tests**: `OrderStoreSuite` updated (or a new Skunk-specific suite added) to test the Postgres-backed `OrderStore` against a mocked/stubbed session or a lightweight fixture — not a real DB — per workflow.md's unit-testing boundary-mocking guidance.

## Non-Functional Requirements
- No behavior change to the `POST /orders` API contract (request/response shapes, status codes) — this track only changes the storage layer.
- Postgres connection pooling sized sanely for local/dev use (not a production-tuned pool).
- Errors from the DB layer (connection failure, constraint violation) surface as typed errors, not raw exceptions leaking to the HTTP layer.

## Acceptance Criteria
- `POST /orders` persists the created order as a row in the `orders` table (verified via Testcontainers integration test).
- Restarting `order-service` does not lose previously created orders (state survives process restart, unlike the current in-memory store).
- Flyway migration applies cleanly on a fresh database and is idempotent on restart (no re-apply of already-applied migrations).
- `sbt "order-service/run"` works end-to-end against the Docker Compose Postgres with no manual DB setup steps beyond `docker compose up`.
- All new/changed config values are sourced from `application.conf` via PureConfig, not raw `sys.env` calls.
- Unit and integration test suites pass; coverage >80% for new code (per workflow.md).

## Out of Scope
- Reservation linkage/display (`Get orders showing reservations` — separate backlog item).
- Order status field / order lifecycle (not part of the current `Order` model).
- Production-grade connection pool tuning, read replicas, or multi-tenancy.
- Any change to `inventory-service`'s persistence (not part of this track).
- Authentication/authorization (product.md non-goal).

## Decision Notes (from track scoping discussion)
- **Skunk over Doobie**: chosen for pure-FP/no-JDBC alignment with purerest's ethos and to avoid a dedicated JDBC blocking thread pool. Not chosen for a weaker effect-typeclass requirement — Skunk's `Network[F]` needs `Async[F]` under the hood (NIO socket ops), and http4s server already requires `Async[F]` regardless of DB library, so there is no constraint-weakening benefit either way.
- **Flyway alongside Skunk**: migrations are independent of the query library. Flyway is JDBC-based, so `org.postgresql:postgresql` (pgjdbc) is added as a build-only dependency solely for Flyway; Skunk still handles all runtime queries.
