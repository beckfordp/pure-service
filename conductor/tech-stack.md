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
- **PostgreSQL**, accessed via **Skunk** or **Doobie** (order-service).

## Observability
- **Tracing**: OpenTelemetry via **otel4s** (Typelevel's Cats-Effect-native library, `oteljava` backend) — server/client purerest middleware propagates a W3C trace context across service-to-service HTTP calls. Console exporter for local/manual verification; in-memory exporter (otel4s's `TracesTestkit`) for automated tests. Real OTLP/collector export deferred.
- **Logging**: log4cats (`log4cats-slf4j` backend) — structured, contextual logging correlated by trace id, via `purerest.logging.Logging`. SLF4J binding is **Logback**, not slf4j-simple — its pattern layout can render MDC values (`%X{trace_id}`/`%X{span_id}`), which log4cats-slf4j populates per log call; slf4j-simple's fixed layout cannot.
- **Metrics**: Prometheus-compatible metrics for request/latency/error rates. (Still deferred to a later track.)

## Resilience
- Retry policies and a circuit breaker, built on Cats Effect primitives — possibly leaning on an existing library (e.g. cats-retry) rather than fully from scratch. Exposed as composable purerest combinators, no annotations.

## Testing
- **munit** — test framework.
- **munit-cats-effect** — lets test bodies return `IO[Unit]` directly, used across all effectful tests.

## Formatting
- **scalafmt** — default Scala 3 style.

## Deferred Concerns

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
- **Trigger to revisit**: the track that extracts `purerest` into its own build/repository and starts
  publishing it as a jar consumed by `order-service`/`inventory-service` (and any future service).
