# Tech Stack

## Language & Build
- **Scala** 3.9.0
- **sbt** — multi-module build: `purerest`, `order-service`, `inventory-service` (`modules/*`)

## Publishing
- **purerest** is locally publishable as a real, versioned jar — `sbt purerest/publishLocal`
  writes to the local Ivy2 cache (`~/.ivy2/local/io.github.beckfordp/purerest_3/<version>/`).
  `order-service`/`inventory-service` still consume it via the internal `ProjectRef`
  (`.dependsOn(purerest)`), not the published jar — this is prep for the eventual real
  extraction, not yet a live consumption path.
- **`smoke-test/`** is a genuinely standalone sbt build (its own `build.sbt`/`project/`,
  deliberately not referenced anywhere in the root `build.sbt`, so `sbt projects`/`compile`/
  `test` at the repo root never sweeps it in) that proves purerest actually works when
  resolved purely as a published jar — an ordinary `libraryDependencies` entry against the
  local Ivy2 cache, the way a real external microservice would consume it, not this repo's
  internal `ProjectRef`. It wraps a stub route with `ServerMetrics.middleware` and asserts a
  real `http.server.request.duration` measurement is recorded, using classes/resources that
  only exist in the published jar. Run via `scripts/archive/verify-purerest-consumption.sh` (publishes
  purerest, resolves its current version, then runs `smoke-test/`'s own `sbt test` against
  it) whenever purerest changes — or manually: `sbt purerest/publishLocal` in the main repo,
  then `cd smoke-test && sbt -DpurerestVersion=<version> test` (the version from
  `sbt purerest/version`; the property is required, not defaulted, so a stale/wrong version
  can't silently pass).
- **Organization**: `io.github.beckfordp`, derived from this project's own GitHub remote
  (`github.com/beckfordp/pure-service`).
- **Versioning**: **sbt-dynver** derives `version` from git tags/commits automatically
  (`ThisBuild`-scoped, so it applies to all three modules, not just purerest) — no manually
  maintained `version :=` to forget to bump. Combined with the previous track's
  `versionScheme := Some("early-semver")` on purerest, a real publish carries both a real
  version and real compatibility metadata.
  - **No git tags exist in this repo yet**, so dynver currently falls back to its
    untagged-history format: `0.0.0+<commit-count>-<sha>+<timestamp>` (e.g.
    `0.0.0+263-74f2a41b+20260923-1436`) rather than a clean semver string. Tag a commit
    (e.g. `git tag v0.1.0`) to get clean `0.1.0`-style versions for an actual release —
    out of scope for this local-publish-only track.
- **No CI/release automation** — `publishLocal` is a manual, on-demand developer action.
  No push to any remote repository (Sonatype, GitHub Packages, etc.) is configured.

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

## Load Testing
- **Gatling** (`modules/load-test`) — generates sustained, realistic traffic against a running
  order-service/inventory-service pair, exercising purerest's RED and resilience metrics the
  way manual curl-based `scripts/archive/verify-*.sh` scripts can't: `gatling-sbt` `4.13.3` (the sbt
  plugin) plus `gatling-charts-highcharts`/`gatling-test-framework` `3.15.1` (Gatling itself —
  versioned independently of the sbt plugin).
- `OrderPlacementSimulation` repeatedly issues `POST /orders` against a configurable base URL
  (default `http://localhost:8080`), ramping to ~15 concurrent users over 30s then holding
  ~5 users/sec for a further 60s (~90s total).
- Simulations run under the plugin's dedicated `Gatling` sbt configuration
  (`sbt loadTest/Gatling/test`), **not** the default `test` task — `loadTest` is deliberately
  left out of root's `.aggregate(...)` in `build.sbt` too, so neither a plain `sbt test` nor
  `sbt compile` at the repo root touches it (confirmed: `sbt projects` still lists `loadTest`,
  but the normal fast dev/test loop's timing is unaffected).
- `scripts/archive/loadtest-purerest.sh` orchestrates two passes against real services (docker-compose
  Postgres + both services): a healthy pass (`INVENTORY_INDUCED_FAILURE_RATE=0`) reporting
  order-service's RED series (`http_server_request_duration_seconds_count`/
  `http_client_request_duration_seconds_count`), then a degraded pass
  (`INVENTORY_INDUCED_FAILURE_RATE=0.3`) reporting its resilience series
  (`purerest_retry_attempts_total`, `purerest_circuit_breaker_state_transitions_total`/
  `_calls_rejected_total`) — both real, nonzero, and (in the degraded pass) genuinely showing
  the circuit breaker tripping and rejecting calls under sustained failure, not just retrying.

## Local Observability Stack
- **Containerization**: **sbt-native-packager** (`JavaAppPackaging` + `DockerPlugin`) builds
  `order-service`/`inventory-service` as Docker images (`sbt orderService/Docker/publishLocal
  inventoryService/Docker/publishLocal`) — `eclipse-temurin:21-jre` base (Debian-based; the
  `-alpine` variant lacks the `bash` the generated launch script needs), `:latest` tag kept in
  sync (`dockerUpdateLatest`), version sanitized for Docker's tag character set
  (`Docker / version := version.value.replace("+", "-")`, since sbt-dynver's version string can
  contain `+`).
- **JSON container logging**: `modules/purerest/src/main/resources/logback-docker.xml`
  (`net.logstash.logback:logstash-logback-encoder`) — selected only inside Docker images via
  `Universal / javaOptions += "-Dlogback.configurationFile=logback-docker.xml"` (not
  `Docker / javaOptions`, which the launch-script-generation task doesn't consume). Plain
  `sbt bgRun` dev still uses the existing plain-text `logback.xml`, unchanged.
- **Orchestration**: `docker-compose.yml` gains `order-service`, `inventory-service`,
  `prometheus`, `grafana`, `elasticsearch`, `kibana`, `filebeat` — all under
  `profiles: ["observability"]`. Plain `docker compose up -d` (the existing `sbt bgRun` dev loop
  and every `scripts/archive/verify-*.sh`) is unaffected and still starts only Postgres; the full stack
  needs `docker compose --profile observability up -d` explicitly.
- **Metrics**: `prom/prometheus:v3.13.3` scrapes both services' existing Prometheus exporter
  endpoints (`observability/prometheus.yml`); `grafana/grafana-oss:13.0.2` is provisioned
  (`observability/grafana/provisioning/`) with a Prometheus datasource and a dashboard
  (`purerest.json`, uid `purerest-red-resilience`) covering request rate, duration percentiles,
  error rate, retry attempts by outcome, circuit-breaker transitions/rejections/current state,
  and order-service DB query duration/error rate.
- **Logs**: `docker.elastic.co/beats/filebeat:8.19.19` tails both services' JSON container logs
  and, via `decode_json_fields`, flattens the app's JSON payload out of Docker's own JSON
  log-driver envelope so fields like `trace_id`/`order_id`/`item`/`quantity` land as top-level,
  independently searchable fields rather than buried in a text blob — shipped to
  `docker.elastic.co/elasticsearch/elasticsearch:8.19.19` (single-node; `discovery.type:
  single-node`, security disabled for local dev) and browsable via
  `docker.elastic.co/kibana/kibana:8.19.19`. Unlike Grafana, Kibana has no file-based
  provisioning for Data Views (index patterns) — a one-shot `kibana-setup` compose service
  (`curlimages/curl`, `depends_on: kibana: condition: service_healthy`) POSTs the
  `purerest-logs-*` Data View and sets it as Kibana's default via its Saved Objects/settings
  HTTP APIs, idempotently, so Discover shows real log data immediately on `docker compose
  --profile observability up -d` instead of requiring a manual one-time setup step.
- **Verification**: `scripts/archive/verify-observability-stack.sh` brings up the profile, runs the
  Gatling load-test module (a healthy pass, then a degraded pass at
  `INVENTORY_INDUCED_FAILURE_RATE=0.5` — not `0.3`; see the script's own comment on why that
  rate specifically makes a circuit-breaker trip reliable rather than a coin flip), and confirms
  — via each system's own HTTP API, not just that containers started — that Prometheus has
  scraped real request/DB-query/resilience metrics, Grafana's datasource and dashboard are
  provisioned and its dashboard's own queries resolve non-empty, and Elasticsearch has indexed
  structured log documents including the degraded pass's
  induced-failure WARN log.

### 2026-09-23: Prometheus exporter's loopback-only default binding
- **Bug found and fixed**: `purerest.metrics.Metrics.oteljava`'s `PrometheusHttpServer.builder()`
  had no explicit host, defaulting to `127.0.0.1`. Invisible in every prior manual/automated
  verification, because those always curled the `/metrics` endpoint from the same host the
  service ran on (loopback works). This track's real docker-compose network — Prometheus running
  in a **sibling container**, not the same host — was the first thing that actually exercised the
  gap, surfacing as "down"/connection-refused Prometheus targets. Fixed with `.setHost("0.0.0.0")`,
  confirmed via `/proc/net/tcp` inspection before/after and a live scrape afterward. A real
  correctness bug in already-shipped code from the original metrics track, not new Phase 4
  functionality — see the fix's own commit for the full root-cause trail.

### 2026-09-23: Single-node Elasticsearch disk watermark
- **Deviation observed**: cluster health went "red" (all shards unassigned) after a burst of
  image builds/runs filled the Docker VM's disk past Elasticsearch's default 90% high watermark.
  Root-caused via `_cluster/allocation/explain`. Fixed by reclaiming disk via `docker builder
  prune -f` (deliberately the safest reclaim category — never touches named images, containers,
  or volumes) and a manual `POST _cluster/reroute?retry_failed=true` to force a retry once space
  was free.
- **"Yellow", not "green", is the correct steady state**: a single-node cluster can never satisfy
  a replica shard (no second node to place it on) — 36/37 primary shards active with one
  unassigned replica is expected, not a bug, for this local single-node setup.

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

### 2026-09-23: Skunk's `otel4s-core` eviction — from a blanket `Level.Warn` to an explicit pin
- **Deviation observed**: Adding Skunk 1.0.0 to `order-service` made `sbt update` fail
  outright — Skunk depends on `otel4s-core` 0.16.0 (its own optional tracing
  integration), which conflicts with this project's pinned `otel4s` 1.1.0. sbt's
  default eviction check treats the 0.x -> 1.x jump as a suspected binary
  incompatibility and fails the build rather than warning.
- **Original resolution (superseded)**: `ThisBuild / evictionErrorLevel := Level.Warn`
  in `build.sbt`, downgrading this class of check to a warning project-wide —
  "highest version wins" (1.1.0) is correct here since nothing in this codebase
  invokes Skunk's otel4s integration. This blanket downgrade also silenced any
  *other* eviction that might arise, not just this one.
- **Current resolution, 2026-09-23**: `evictionErrorLevel` is back to `Level.Error`
  (see the transitive-version-drift concern below, option 4), and this specific
  eviction is instead pinned via three explicit `ThisBuild / dependencyOverrides`
  entries (`otel4s-core`/`-core-common`/`-core-metrics` at `otel4sVersion`), commented
  with the same reasoning. Same outcome for this one case, but no longer a blanket
  suppression — any *other* eviction now fails the build instead of passing silently.
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
- **Options to address when this split happens**:
  1. Narrow purerest's own public API so consumers never import the underlying library's types
     directly (e.g. wrap tapir behind a purerest-owned endpoint-builder DSL) — removes the diamond
     entirely for that dependency, since only purerest would declare it. **Not yet decided, revisit
     when the extraction happens.**
  2. Adopt MiMa (Migration Manager) in purerest's own release CI to enforce binary compatibility
     within a major version line, making "highest version wins" resolution provably safe rather than
     hopefully safe. **Not yet decided, revisit when the extraction happens.**
  3. **Done (prep), 2026-09-23**: `purerest / versionScheme := Some("early-semver")` is set in
     `build.sbt`, so sbt's eviction-warning machinery has real semver metadata to reason about once
     purerest is actually published. Not yet exercised against a real external consumer — purerest
     is still consumed via `.dependsOn(purerest)`, not a published jar — so this is groundwork, not
     a tested guarantee.
  4. **Done (prep), 2026-09-23**: `ThisBuild / evictionErrorLevel := Level.Error` (was `Level.Warn`).
     Flipping it immediately caught the eviction below, confirming the guardrail actually works
     rather than passing by default.
- **Skunk's `otel4s-core` eviction, now pinned explicitly**: Skunk 1.0.0 depends on
  `otel4s-core`/`-core-common`/`-core-metrics` 0.16.0 (its own optional tracing integration), which
  conflicts with this build's pinned otel4s 1.1.0 — an early-semver 0.x -> 1.x jump that
  `evictionErrorLevel := Level.Error` now fails the build on by default. "Highest version wins" is
  the correct resolution (nothing in this project invokes Skunk's otel4s integration), so these
  three coordinates are pinned via `ThisBuild / dependencyOverrides` in `build.sbt`, with a comment
  explaining why — an explicit, intentional override instead of a blanket `Level.Warn` suppression
  hiding it (and every other eviction) by default.
- **If/when option 4 (`evictionErrorLevel := Error`) is relied on against a real external
  consumer, don't treat it as self-sufficient**: sbt's eviction report (same content whether it's a
  warning or a build-failing error) names the conflicting artifact, the selected vs. evicted
  version(s), and which direct dependency requested each — enough for a consumer to *locate* a
  conflict and know what to change (bump their pin, or add a `dependencyOverride`). What it can't
  tell them is whether the fix is *safe*, or whether the conflict is even real — "suspected binary
  incompatible" is just a `versionScheme` guess, and this project already hit a false positive from
  it (the Skunk/otel4s-core case above, before it was pinned explicitly). Option 3's real
  `versionScheme` metadata on purerest's own published artifact is what makes that guess
  trustworthy for purerest's own version bumps specifically — it doesn't cover every third-party
  library in the diamond, only purerest's own coordinate.
- **Trigger to revisit**: the track that extracts `purerest` into its own build/repository and starts
  publishing it as a jar consumed by `order-service`/`inventory-service` (and any future service) —
  that's when options 3 and 4 get their first real exercise against an external consumer, and when
  options 1 and 2 (still undecided) need a decision.
