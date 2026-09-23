# Tech Stack

## Language & Build
- **Scala** 3.9.0
- **sbt** — multi-module build: `purerest`, `order-service`, `inventory-service` (`modules/*`)

## Effect System
- **Cats Effect 3** — the effect system underpinning purerest and both services. All public APIs are tagless-final / typeclass-based (`F[_]: Async`, etc.).

## HTTP
- **http4s** — server and client, for both inbound (order-service, inventory-service) and outbound (purerest's resilient client) HTTP.

## API Documentation
- **tapir** (`tapir-core`, `tapir-http4s-server`, `tapir-json-circe`, `tapir-openapi-docs`, `tapir-swagger-ui-bundle`, version 1.11.25) — endpoints are described once as tapir `Endpoint`/`ServerEndpoint` values; `purerest.docs` interprets that same value into both the real `HttpRoutes[F]` (via `tapir-http4s-server`) and a generated OpenAPI spec + browsable Swagger UI (via `tapir-openapi-docs`/`tapir-swagger-ui-bundle`), so routes and docs can't drift apart. Replaces hand-written `HttpRoutes.of[F] { case ... }` pattern matches in `order-service`/`inventory-service`.

## JSON
- **circe** — JSON encoding/decoding, via `http4s-circe` for http4s integration. Chosen for its idiomatic Typelevel fit, mature http4s support, and typed decode errors (aligns with the typed-error-handling guideline).

## Persistence
- **PostgreSQL**, accessed via **Skunk** (`org.tpolecat`) — order-service's query library.
- **Migrations**: **Flyway**, run automatically on `order-service` startup. Flyway is
  JDBC-based, so `org.postgresql:postgresql` (pgjdbc) is added as a **build-only**
  dependency solely for Flyway — Skunk still handles all runtime queries.
- **Configuration**: **PureConfig**, loading an `application.conf` at startup. Covers
  the Postgres connection plus settings previously read ad hoc from `sys.env`
  (`ORDER_SERVICE_PORT`, `INVENTORY_SERVICE_BASE_URL`).
- **Testing**: **Testcontainers** (`testcontainers-scala-postgresql`) spins up a real,
  ephemeral Postgres for integration tests. Local/manual dev uses a separate
  `docker-compose.yml` Postgres container (not a library dependency).

### 2026-09-23: Skunk chosen over Doobie
- Skunk was chosen for its pure-FP, no-JDBC fit with purerest's ethos — no dedicated
  blocking thread pool for DB calls (Doobie/JDBC ties up one OS thread per in-flight
  query even under Cats Effect 3's thread-shifting).
- **Not** chosen for a weaker effect-typeclass requirement: Skunk's `Network[F]`
  (fs2's NIO socket layer) needs `Async[F]` under the hood regardless, and http4s
  server already requires `Async[F]` for any DB library choice — so there's no
  constraint-weakening benefit either way.
- Trade-off accepted: smaller community/fewer examples than Doobie, and no
  equivalent to `doobie-munit`'s compile-time SQL-vs-schema `.check`/`.analyze`.

## Observability
- **Tracing**: OpenTelemetry via **otel4s** (Typelevel's Cats-Effect-native library, `oteljava` backend) — server/client purerest middleware propagates a W3C trace context across service-to-service HTTP calls. Console exporter for local/manual verification; in-memory exporter (otel4s's `TracesTestkit`) for automated tests. Real OTLP/collector export deferred.
- **Logging**: log4cats (`log4cats-slf4j` backend) — structured, contextual logging correlated by trace id, via `purerest.logging.Logging`. SLF4J binding is **Logback**, not slf4j-simple — its pattern layout can render MDC values (`%X{trace_id}`/`%X{span_id}`), which log4cats-slf4j populates per log call; slf4j-simple's fixed layout cannot.
- **Metrics**: OpenTelemetry via **otel4s**'s `Meter[F]` API (same `oteljava` backend already used for tracing) — RED metrics (request rate/errors/duration) for purerest's inbound/outbound HTTP paths, plus resilience signals (retry attempts, circuit breaker state transitions). Exported via the OTel SDK's `opentelemetry-exporter-prometheus` (a scrape endpoint per service); in-memory `MetricsTestkit` for automated tests, mirroring tracing's `TracesTestkit` pattern.

### 2026-09-23: otel4s `Meter[F]` chosen over a separate metrics library
- No wrapping layer needed — otel4s is already idiomatic Cats-Effect-native (unlike
  resilience4j), so `Meter[F]` is exposed directly as part of purerest's public API,
  matching how `purerest.tracing` already exposes otel4s's `Tracer[F]` directly.
- `opentelemetry-exporter-prometheus` resolved cleanly at `${openTelemetryVersion}-alpha`
  (`1.66.0-alpha`) — this OTel component is still incubating/alpha upstream, tracking
  the main `opentelemetry-java` release train rather than a stable version of its own.
- OTLP/collector push export deferred, same as tracing's real-OTLP-export deferral —
  Prometheus scrape only for this track.

## Resilience
- **Retry**: **cats-retry** (`com.github.cb372`) — composable retry policies (exponential
  backoff, jitter, max attempts) on Cats Effect.
- **Circuit breaker**: **resilience4j-circuitbreaker**'s core, non-reactive
  `CircuitBreaker` class, wrapped as an internal engine driven manually via explicit
  `tryAcquirePermission`/`onResult`/`onError` sequencing — never exposed in purerest's
  public API.
- Both exposed as composable purerest combinators (`purerest.resilience`), no
  annotations — matching purerest's existing `ClientTracing.middleware`/
  `ServerTracing.middleware` shape.

### 2026-09-23: cats-retry + resilience4j-circuitbreaker chosen
- **cats-retry 4.0.0**: a new major version, published for **Scala 3 only** (matches
  this project exactly) with a rewritten, more powerful "result handler" API replacing
  v3.x's `wasSuccessful`/`isWorthRetrying` hooks. Chosen over the older v2.1.2/v3.1.3
  line since this is a greenfield adoption — no reason to start on the superseded API.
- **resilience4j-circuitbreaker**: no mature, idiomatic Cats-Effect-native circuit
  breaker library exists (unlike Java's resilience4j). Rather than hand-rolling a
  sliding-window/failure-rate state machine from scratch, resilience4j's core engine
  (its plain in-memory state machine, not its Spring/reactive integration modules) is
  wrapped behind a pure combinator — `acquirePermission`/`onSuccess`/`onError` are
  synchronous, non-blocking, in-memory state updates, safe to drive from Cats Effect
  this way. Gets mature failure-rate/slow-call-duration-based tripping logic without
  reinventing its subtleties, while keeping purerest's own API 100% idiomatic
  tagless-final Cats Effect — resilience4j types never leak into it.
- `CircuitBreakerConfig(failureThreshold: Int, resetTimeout: FiniteDuration)` maps onto
  resilience4j's rate-based engine as a count-based sliding window of exactly
  `failureThreshold` calls with a 100% failure-rate threshold (`slidingWindowSize` =
  `minimumNumberOfCalls` = `failureThreshold`, `failureRateThreshold = 100.0f`) —
  giving simple "N consecutive failures" semantics from a plain integer, since
  resilience4j has no raw-count-based mode natively.
- **Bug found and fixed**: a 5xx `Response` is a normal returned value from
  `Client[F].run`, not a thrown exception — so recording only `onSuccess`/`onError`
  (success vs. thrown-exception) let 5xx responses silently count as breaker
  successes, and the breaker could never trip from them. Fixed via resilience4j's
  `CircuitBreakerConfig.recordResult(Predicate<Object>)`, classifying a 5xx `Response`
  as a failure, and calling `breaker.onResult(...)` (which applies that predicate)
  instead of `onSuccess(...)` for every returned response.

### 2026-09-23: `Retry.middleware` delegates its execution loop to http4s's own `Retry`
- **Deviation observed**: retrying a `Client[F].run` call safely is non-trivial — each
  failed attempt's `Resource[F, Response[F]]` (connection + body) must be released
  before trying again, and the winning attempt's `Resource` must stay open for the
  eventual caller. Hand-rolling this with cats-retry directly (via `.allocated` and
  manual finalizer bookkeeping) would re-implement a problem **http4s's own
  `org.http4s.client.middleware.Retry`** already solves correctly (it uses
  `cats.effect.std.Hotswap` internally for exactly this).
- **Resolution**: `purerest.resilience.Retry.middleware` uses http4s's `Retry` for the
  actual execution loop, and uses cats-retry's `RetryPolicies` (`limitRetries` +
  `exponentialBackoff`, composed via `.join`, evaluated under `cats.Id`) purely to
  compute the backoff schedule fed into http4s's `RetryPolicy` — genuinely using
  cats-retry for policy composition, without reinventing Resource-safe retry
  execution.
- **cats-retry's `fullJitter` not used**: http4s's backoff slot is a *pure* function
  (`Int => Option[FiniteDuration]`), but cats-retry's `fullJitter` needs an effectful
  `Random[F]` — incompatible. Retry delays are deterministic exponential backoff
  (`limitRetries` + `exponentialBackoff`) rather than jittered. Revisit if the lack of
  jitter causes noticeable thundering-herd retries in practice.
- **Logging**: http4s's `Retry` requires a `LoggerFactory[F]` context bound
  regardless of whether its own internal logging is used; supplied
  `log4cats-core`'s `NoOpFactory[F]` and pass `logRetries = false`, since this
  project's convention is an explicitly-passed `StructuredLogger[F]` value (see
  `purerest.logging.Logging`), not implicit `LoggerFactory[F]` summoning. Retry
  activity is instead logged by `Retry.middleware` itself, via the response's
  `Retry.AttemptCountKey` attribute (successes) and `.onError` (final exhausted
  failures) — using the passed-in `StructuredLogger[F]` directly.

## Testing
- **munit** — test framework.
- **munit-cats-effect** — lets test bodies return `IO[Unit]` directly, used across all effectful tests.

## Formatting
- **scalafmt** — default Scala 3 style.

## Deferred Concerns

### 2026-09-23: `docker-java.properties` pins Docker API version for Testcontainers
- **Deviation observed**: `MigrationsSuite` (Testcontainers Postgres) failed every run
  with "Could not find a valid Docker environment", even with a working, fully up
  Docker Desktop daemon. Root cause: a known upstream incompatibility
  (testcontainers/testcontainers-java#11210, #11212, #11360) — testcontainers-scala
  0.43.6 bundles a docker-java client that hardcodes Docker API version 1.32, and this
  machine's Docker Desktop (Engine 29.5.2) enforces `MinAPIVersion: 1.40`, rejecting
  every request with a blank-stub HTTP 400 before any container starts.
- **Resolution**: added `modules/order-service/src/test/resources/docker-java.properties`
  with `api.version=1.44` (within the daemon's supported 1.40-1.54 range), which
  docker-java reads from the classpath to skip its broken default negotiation. Neither
  the `DOCKER_API_VERSION` env var nor an sbt session restart alone fixed it — the
  `DockerDesktopClientProviderStrategy` docker-java uses here doesn't honor that env var.
- **Revisit when**: testcontainers-scala ships on Testcontainers 2.x (which negotiates
  the API version with the daemon instead of hardcoding 1.32) — the properties file can
  likely be removed then.

### 2026-09-23: `ThisBuild / evictionErrorLevel := Level.Warn`
- **Deviation observed**: Adding Skunk 1.0.0 to `order-service` made `sbt update` fail
  outright — Skunk depends on `otel4s-core` 0.16.0 (its own optional tracing
  integration), which conflicts with this project's pinned `otel4s` 1.1.0. sbt's
  default eviction check treats the 0.x -> 1.x jump as a suspected binary
  incompatibility and fails the build rather than warning.
- **Resolution**: `ThisBuild / evictionErrorLevel := Level.Warn` in `build.sbt`,
  downgrading this class of check to a warning project-wide — "highest version wins"
  (1.1.0) is correct here since nothing in this codebase invokes Skunk's otel4s
  integration.
- **Relates to** the transitive-version-drift concern below: this is the same
  underlying sbt/Coursier eviction mechanism, triggered earlier than expected (by a
  third-party library's own dependency, not by the purerest-extraction split this
  section otherwise anticipates).

### Transitive version drift once purerest becomes a published artifact
- **Today**: `purerest`, `order-service`, and `inventory-service` are subprojects of one sbt build,
  and every shared library version (`tapirVersion`, `http4sVersion`, `catsEffectVersion`, `circeVersion`,
  etc.) is a single `val` in the root `build.sbt`, referenced by all modules that need it. Version
  drift between purerest and its consumers (e.g. `order-service` resolving a different `tapir-core`
  than the one `purerest` was built against) is structurally impossible right now — there's only
  ever one version on record.
- **The problem, deferred**: When `purerest` is split out into its own build and published as a jar
  (the planned move for it to anchor a wider microservices platform), that safety net disappears.
  purerest's transitive dependencies get baked into its published POM at release time; each consuming
  service's `build.sbt` becomes an independent second author of version constraints for the same
  libraries (e.g. `tapir-core`). sbt/Coursier still resolves this the same way it resolves any
  diamond dependency — flattens the graph, picks the **highest** requested version per coordinate,
  emits an eviction warning if a library's `versionScheme` metadata suggests the jump is
  binary-incompatible — but nothing *forces* purerest's and a consumer's independently-declared
  versions to stay in sync any more, and a binary-incompatible eviction only warns, it doesn't fail
  the build, by default.
- **Options to address when this split happens** (not yet decided, revisit then):
  1. Narrow purerest's own public API so consumers never import the underlying library's types
     directly (e.g. wrap tapir behind a purerest-owned endpoint-builder DSL) — removes the diamond
     entirely for that dependency, since only purerest would declare it.
  2. Adopt MiMa (Migration Manager) in purerest's own release CI to enforce binary compatibility
     within a major version line, making "highest version wins" resolution provably safe rather than
     hopefully safe.
  3. Publish purerest with `versionScheme := "early-semver"` (or similar) so sbt's existing
     eviction-warning machinery has real semver metadata to reason about.
  4. As a blunt fallback in consumers: `ThisBuild / dependencyOverrides` to pin shared transitive
     versions centrally, and/or `evictionErrorLevel := Level.Error` to fail the build on an
     incompatible eviction instead of only warning.
- **If option 4 is used, pair it with option 3 — don't use `evictionErrorLevel := Error` alone**:
  sbt's eviction report (same content whether it's a warning or, with `Level.Error`, a build-failing
  error) names the conflicting artifact, the selected vs. evicted version(s), and which direct
  dependency requested each. For the one-hop diamond a consumer/purerest pair produces, that's
  exactly the two culprits (purerest's declared version vs. the consumer's own), so the message is
  enough for a consumer to *locate* the conflict and know what to change (bump their pin, or add a
  `dependencyOverride`). What it can't tell them is whether the fix is *safe*, or whether the
  conflict is even real — "suspected binary incompatible" is just a `versionScheme` guess, and this
  project already hit a false positive from it (see the `evictionErrorLevel := Level.Warn` entry
  below: Skunk's `otel4s-core` 0.16.0 vs. this build's pinned 1.1.0 failed resolution even though
  nothing here uses Skunk's otel4s integration). Without real `versionScheme` metadata on purerest's
  published artifact, `Level.Error` risks blocking a consumer's build on a bump that's actually
  harmless, with nothing in the error message to tell them so. Publishing purerest with
  `versionScheme := "early-semver"` (option 3) gives the heuristic real semver information instead of
  a guess, so the error's implicit "this is unsafe" claim is trustworthy and the remedy it points
  to (bump or override) can be applied with confidence rather than guesswork.
- **Trigger to revisit**: the track that extracts `purerest` into its own build/repository and starts
  publishing it as a jar consumed by `order-service`/`inventory-service` (and any future service).
