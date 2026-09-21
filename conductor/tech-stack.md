# Tech Stack

## Language & Build
- **Scala** 3.9.0
- **sbt** — multi-module build: `purerest`, `order-service`, `inventory-service` (`modules/*`)

## Effect System
- **Cats Effect 3** — the effect system underpinning purerest and both services. All public APIs are tagless-final / typeclass-based (`F[_]: Async`, etc.).

## HTTP
- **http4s** — server and client, for both inbound (order-service, inventory-service) and outbound (purerest's resilient client) HTTP.

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
