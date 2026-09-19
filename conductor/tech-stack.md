# Tech Stack

## Language & Build
- **Scala** 3.9.0
- **sbt** (single module today; expected to grow into a multi-module build: `purerest`, `order-service`, `inventory-service`)

## Effect System
- **Cats Effect 3** — the effect system underpinning purerest and both services. All public APIs are tagless-final / typeclass-based (`F[_]: Async`, etc.).

## HTTP
- **http4s** — server and client, for both inbound (order-service, inventory-service) and outbound (purerest's resilient client) HTTP.

## JSON
- **circe** — JSON encoding/decoding, via `http4s-circe` for http4s integration. Chosen for its idiomatic Typelevel fit, mature http4s support, and typed decode errors (aligns with the typed-error-handling guideline).

## Persistence
- **PostgreSQL**, accessed via **Skunk** or **Doobie** (order-service).

## Observability
- **Tracing**: OpenTelemetry — propagated across service-to-service HTTP calls.
- **Logging**: log4cats — structured, contextual logging correlated by trace id.
- **Metrics**: Prometheus-compatible metrics for request/latency/error rates.

## Resilience
- Retry policies and a circuit breaker, built on Cats Effect primitives — possibly leaning on an existing library (e.g. cats-retry) rather than fully from scratch. Exposed as composable purerest combinators, no annotations.

## Testing
- **munit** — test framework (already present in the scaffold).

## Formatting
- **scalafmt** — default Scala 3 style.
