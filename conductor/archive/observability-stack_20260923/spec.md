# Spec: Stand up a local production-like observability stack

## Overview
Add a local, docker-compose-based observability stack — Prometheus + Grafana for metrics dashboards, Elasticsearch + Filebeat + Kibana for trace-correlated logs — wired to order-service/inventory-service's real output, and make it the README's primary reference for building, running, and observing the whole system. Current logging (2 lines total) and metrics (HTTP-layer + resilience-event only) are both thin, so this track enriches both first — including two previously-deferred metrics from the original metrics track. Kibana needs container logs, so both services get containerized (via sbt-native-packager). The existing `scripts/verify-*.sh` (sbt `bgRun`-based) workflow and plain `docker compose up -d` (Postgres only) are left completely unaffected — the new stack is additive and opt-in via a Compose profile.

## Functional Requirements
1. **Enrich logging** in order-service/inventory-service: request-received/completed logging on all endpoints (incl. `GET /orders/{id}`'s 404 path); error-path logging wherever a typed error/exception currently surfaces silently; log `InventoryRoutes.maybeInduceFailure`'s trigger at warn level; log effective config at startup; pass order id/item/quantity/reservation id as `StructuredLogger` metadata fields (Kibana-filterable), not string interpolation.
2. **Enrich metrics** (both previously deferred in the metrics track's own "Out of Scope"): a live circuit-breaker state gauge (current CLOSED/OPEN/HALF_OPEN, not just historical counts); DB query duration + error-rate metrics for order-service's Postgres/Skunk calls, instrumented directly in order-service.
3. Containerize `order-service`/`inventory-service` via `sbt-native-packager` `1.11.1` (`dockerBaseImage := "eclipse-temurin:21-jre-alpine"`, `Docker / dockerUpdateLatest := true`).
4. Add `logback-docker.xml` to purerest (JSON via `net.logstash.logback:logstash-logback-encoder:9.0`) alongside the existing `logback.xml` (unchanged for local `sbt bgRun`); Docker images select it via `-Dlogback.configurationFile=logback-docker.xml`.
5. Extend `docker-compose.yml` (all behind a `profiles: ["observability"]` Compose profile — plain `docker compose up -d` still starts only Postgres): `order-service`/`inventory-service` (from #3), `prometheus` (`v3.13.3`) scraping both, `grafana` (`13.0.2`) with a provisioned datasource, `elasticsearch`/`kibana` (`8.19.19`, security disabled, local-dev-only), `filebeat` (`8.19.19`) shipping both services' container logs to Elasticsearch.
6. Provision one Grafana dashboard (JSON): request rate, duration percentiles, error rate, retry attempts by outcome, circuit-breaker transitions/rejections/current state, order-service DB query duration/error rate.
7. Add `scripts/verify-observability-stack.sh`: brings up the full profile, runs the Gatling load-test module (healthy + degraded passes), confirms Prometheus scraped both targets (incl. new series), Grafana's dashboard provisioned, and Elasticsearch indexed log documents with `trace_id` from both services (incl. an induced-failure warning). Prints the Grafana/Kibana URLs.
8. Document the stack's design in `tech-stack.md`.
9. **Update `README.md`** with a new, prominent section — the primary "how to build, run, and observe this system" reference — covering: building the Docker images, starting the stack (`docker compose --profile observability up -d`, or running `scripts/verify-observability-stack.sh` for a one-shot including traffic generation), generating load (the Gatling load test), and monitoring results (Grafana URL + which panels to look at; Kibana URL + an example `trace_id` search walkthrough).

## Non-Functional Requirements
- New logging/metrics follow `product-guidelines.md`'s conventions — additive, no exceptions for control flow.
- Zero impact on the existing `sbt bgRun`-based `scripts/verify-*.sh` workflow or plain `docker compose up -d`.
- Full existing test suite stays green.
- Elasticsearch/Kibana run without TLS/authentication — local-dev-only.

## Acceptance Criteria
- `docker compose --profile observability up -d` starts all 8 services healthy; plain `docker compose up -d` still starts only Postgres.
- A Kibana search for a given `trace_id` returns log entries from **both** services for that request.
- Grafana's dashboard shows real data for every panel after the load test runs.
- `scripts/verify-observability-stack.sh` passes end-to-end.
- `README.md` lets a reader unfamiliar with the project build, run, generate load against, and observe results from the whole stack, start to finish.
- `tech-stack.md` documents the stack's design.

## Out of Scope
- Remote/cloud deployment; TLS/authentication hardening; CI integration; alerting/alertmanager.
- JVM/process metrics (heap, GC, threads).
- A new purerest DB-metrics abstraction (order-service-only instrumentation for now).
- Replacing the existing `scripts/verify-*.sh` sbt-`bgRun` pattern.
