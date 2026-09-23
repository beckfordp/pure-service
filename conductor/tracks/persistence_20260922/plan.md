# Plan: Order Persistence (Skunk + PostgreSQL)

## Phase 0: Tech Stack & Build Setup
- [x] Task: Update `conductor/tech-stack.md` — document Skunk (not Doobie) as the Postgres access library, Flyway for migrations, PureConfig for configuration, Testcontainers for integration-test Postgres. Dated note explaining the choice (per workflow.md principle: tech-stack changes documented before implementation). [9664ef9]
- [x] Task: Add shared dependency versions to `build.sbt` (`skunkVersion`, `flywayVersion`, `pureconfigVersion`, `testcontainersVersion`/`testcontainers-scala-postgresql`) and add them to the `orderService` module's `libraryDependencies` (`org.tpolecat %% skunk-core`, `org.flywaydb % flyway-database-postgresql` + `org.postgresql % postgresql` as a build-only JDBC driver for Flyway, `com.github.pureconfig %% pureconfig-core`; Testcontainers as `% Test`). [ff59d9a]
- [x] Task: Conductor - User Manual Verification 'Phase 0: Tech Stack & Build Setup' (Protocol in workflow.md) — autonomous run: verified via `sbt orderService/update` succeeding (warning-only eviction, documented in tech-stack.md); no interactive walkthrough needed for a dependency-resolution-only phase.

## Phase 1: Configuration (PureConfig) [checkpoint: 43d74b6]
- [x] Task: Write failing test for a new `OrderServiceConfig` case class (port, inventory base URL, Postgres host/port/db/user/password) loaded via PureConfig from `application.conf` (Red). [0531fd0]
- [x] Task: Implement `application.conf` + `OrderServiceConfig`/`PureConfig` loader; replace `sys.env.get(...)` calls in `Main.scala` with config-driven values (Green). [160b7d3]
- [x] Task: Refactor — tidy config case class structure/naming (Optional). No refactor needed — `OrderServiceConfig`/`PostgresConfig` are already minimal, flat case classes.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Configuration (PureConfig)' (Protocol in workflow.md) — autonomous run: added `scripts/verify-order-service-config.sh` (boots order-service with default config, then with `ORDER_SERVICE_PORT` override) and ran it directly; both checks passed. [43d74b6]

## Phase 2: Schema & Migrations (Flyway)
- [ ] Task: Write failing test verifying Flyway applies a migration creating the `orders` table against a Testcontainers Postgres (Red).
- [ ] Task: Add Flyway migration `V1__create_orders_table.sql` (`id UUID PRIMARY KEY`, `item TEXT NOT NULL`, `quantity INT NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`); wire Flyway to run on `order-service` startup using config-driven connection settings (Green).
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Schema & Migrations (Flyway)' (Protocol in workflow.md)

## Phase 3: Skunk-backed OrderStore
- [ ] Task: Write failing unit tests for a new `OrderStore.postgres`/Skunk-backed implementation against a mocked/stubbed Skunk `Session` (Red).
- [ ] Task: Implement `OrderStore.postgres[F]` using a pooled Skunk `Session.pooled` Resource, satisfying the existing `OrderStore[F]` trait (`create(item, quantity): F[Order]`) (Green).
- [ ] Task: Write failing Testcontainers integration test: `POST /orders` end-to-end persists a row queryable back from Postgres (Red).
- [ ] Task: Wire `OrderStore.postgres` into `Main.scala` in place of `OrderStore.inMemory`, using config-driven connection settings; make the integration test pass (Green).
- [ ] Task: Refactor — review error handling (DB connection failure, constraint violations) maps to typed errors, not leaked exceptions (Optional).
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Skunk-backed OrderStore' (Protocol in workflow.md)

## Phase 4: Local Dev Environment (Docker Compose)
- [ ] Task: Add `docker-compose.yml` provisioning a Postgres container matching `application.conf` defaults, for `sbt "order-service/run"` and manual testing.
- [ ] Task: Update relevant docs (e.g. README/run script) with `docker compose up` as a prerequisite step for running order-service locally.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Local Dev Environment (Docker Compose)' (Protocol in workflow.md)

## Phase 5: Coverage & Cleanup
- [ ] Task: Run `sbt coverage test coverageReport`; confirm >80% coverage for new code, add tests to close gaps.
- [ ] Task: Run `sbt scalafmtCheck test` and resolve any issues.
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Coverage & Cleanup' (Protocol in workflow.md)
