# Product Guide

## Vision
`pure-service` is the home of **purerest** — a pure-functional-programming platform library
(Scala 3, Cats Effect, http4s) that gives every microservice in an estate the same
observability (tracing, structured logging, metrics) and resilience (retry, circuit breaker)
infrastructure, consistently, as composable building blocks rather than per-service
reinvention. The open question this project exists to answer is *what "right" looks like* for
that infrastructure: purerest wraps established libraries (otel4s, resilience4j, cats-retry),
but their defaults and configuration are only genuinely validated by exercising them under
real, controlled load — not assumed correct because they're wired up. Two reference services,
**order-service** and **inventory-service**, are built on top of purerest to drive its design
and prove it out end-to-end.

## Target Users
- Internal engineering (primarily the author) — a personal platform-engineering project and learning vehicle for pure FP in Scala 3.
- Any future team/service that would adopt purerest as its microservice foundation.

## Core Use Case
`order-service` receives `POST /orders`, reserving stock via a resilient call to
`inventory-service`. This call path is purerest's design driver: a trace/span propagated across
the call, structured logs correlated by trace id on both sides, RED + resilience metrics for
both the inbound and outbound call, and a client that retries transient failures and trips a
circuit breaker under sustained failure — all composed via purerest's API, no annotations on
either service.

`inventory-service`'s failure/latency can be induced so it can validate purerest's resilience
behavior under controlled conditions — currently only at container startup; making this
runtime-adjustable is part of Iteration 2 below.

## Customer Journey (context)
The Core Use Case above is the technical design driver, not a commitment to build a full
storefront. Only two steps of a real order lifecycle exist: **place order** (`POST /orders` —
reserve stock, persist, return the order + reservation id) and **check status** (`GET
/orders/{id}`, including a modeled 404 for an unknown id). Reservations never expire or
release, and payment/fulfillment don't exist — real gaps if the journey were ever extended
toward a real storefront, but out of current scope.

## Components
1. **purerest** (library) — tracing/propagation (OpenTelemetry), structured contextual logging
   (log4cats), metrics (Prometheus-compatible RED + resilience signals), a resilient HTTP
   client (retry + circuit breaker), and self-documenting endpoints (tapir: real routes + an
   always-in-sync OpenAPI spec/Swagger UI from one definition).
2. **order-service** — http4s REST API backed by PostgreSQL/Skunk; orchestrates order
   placement via purerest's resilient client.
3. **inventory-service** — http4s REST API exposing stock reservation; a second, independent
   reference consumer of purerest, deliberately made slow/flaky to exercise its resilience.

## Key Features (built)
- purerest: tracing, structured logging, metrics, retry, circuit breaker — as library building blocks
- order-service: `POST /orders`, order persistence, resilient call to inventory-service
- inventory-service: stock reservation endpoint(s)
- Local observability stack (Docker Compose profile): Prometheus/Grafana/Elasticsearch/Kibana/Filebeat
  wired to real request/DB/resilience metrics and correlated structured logs — see README's
  "Build, run, and observe this system"

## Iteration 2 Goals (2026-09-23)
Hands-on use of the finished stack raised the question purerest hasn't actually answered yet:
**is this resilience/observability infrastructure good, and how would we know?**

1. **Validate resilience under controlled load.** Make inventory-service's induced-failure rate
   runtime-adjustable (not just startup-config), drive it through a Gatling scenario, and use
   the result to answer concretely — do the existing retry/circuit-breaker Grafana panels show
   *effective* behavior (successful retries, timely trips, timely recovery), or just activity?
2. **Make purerest a real, consumable library.** A tag-triggered release/publish pipeline,
   Scaladoc for the public API, and — later — a service template so adopting purerest starts
   from a working example instead of a blank `build.sbt`.
3. **Reduce operational noise.** `scripts/` has been archived wholesale pending a decision on
   what's still genuinely useful; most were one-shot verification artifacts from completed
   tracks, not tools anyone reaches for day to day.

## Non-Goals (for now)
- Authentication/authorization
- Multi-service deployment/orchestration (Kubernetes, etc.) — local/dev focus first
- Full customer-journey buildout (catalog, payment, fulfillment) — purerest's design driver, not a commitment
- A dedicated experiment-control UI — worth revisiting once Iteration 2's runtime-adjustable
  failure rate exists, not before
