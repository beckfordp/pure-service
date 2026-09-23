## pure-service

`purerest` is a reusable Cats-Effect/http4s microservice platform library, exercised by two
reference services: `order-service` and `inventory-service`.

## Build, run, and observe this system

This is the main reference for exercising the whole system end to end — building both services,
running them with a full production-like observability stack, and watching real request, retry,
circuit-breaker, and log data flow through Prometheus, Grafana, and Kibana.

### Prerequisites
- sbt / JDK (for building)
- Docker Desktop (or another Docker engine) with Docker Compose v2

### 1. Build the service images

```
sbt orderService/Docker/publishLocal inventoryService/Docker/publishLocal
```

This produces `order-service:latest` and `inventory-service:latest` local Docker images (via
`sbt-native-packager`), each with JSON container logging baked in for the stack below.

### 2. Bring up the full stack

```
docker compose --profile observability up -d
```

This starts Postgres, both services, and the observability stack — Prometheus, Grafana,
Elasticsearch, Kibana, and Filebeat — all networked together. It takes a minute or two on first
run while images are pulled and Elasticsearch/Kibana finish starting.

> Plain `docker compose up -d` (no `--profile`) starts **only Postgres**, unchanged — that's the
> lightweight path used by `sbt bgRun`/`scripts/archive/run-services.sh` day-to-day dev and by
> `scripts/archive/verify-*.sh`. The full stack above is opt-in. (All `scripts/` are archived
> pending a cleanup decision; they still work from their new path.)

Once it's up:

| Service | URL |
|---|---|
| order-service | http://localhost:8080 |
| inventory-service | http://localhost:8081 |
| Grafana dashboard | http://localhost:3000/d/purerest-red-resilience |
| Kibana | http://localhost:5601 |
| Prometheus | http://localhost:9092 |
| Elasticsearch | http://localhost:9200 |

Grafana and Kibana need no login (anonymous admin access, local-dev only — see
`conductor/tech-stack.md`).

### 3. Generate some traffic

```
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" \
  -d '{"item":"widget","quantity":3}'
```

To see resilience behavior (retries, circuit-breaker trips) instead of the happy path, restart
inventory-service with a nonzero induced-failure rate and repeat:

```
INVENTORY_INDUCED_FAILURE_RATE=0.3 \
  docker compose --profile observability up -d --force-recreate --no-deps inventory-service
```

For sustained, realistic load rather than one-off curls, run the Gatling load-test module against
the running stack:

```
sbt "loadTest/Gatling/test"
```

### 4. Observe

- **Grafana** (http://localhost:3000/d/purerest-red-resilience) — request rate, duration
  percentiles, error rate, retry attempts by outcome, circuit-breaker transitions/rejections/
  current state, and order-service DB query duration/error rate, all live.
- **Kibana** (http://localhost:5601 → Discover) — structured JSON logs from both services,
  searchable by field: `trace_id`, `order_id`, `item`, `quantity`, `reservation_id`,
  `logger_name`, `level`, etc. An induced failure shows up as a WARN-level "Induced failure
  triggered" log with the triggering item/quantity attached. The `purerest-logs-*` Data View is
  provisioned automatically (via the `kibana-setup` compose service) and set as default, so
  Discover shows real data immediately — no manual index-pattern setup needed.
- **Prometheus** (http://localhost:9092) — the raw metrics Grafana's dashboard queries, useful for
  ad-hoc PromQL.

### 5. Automated end-to-end verification

```
./scripts/archive/verify-observability-stack.sh
```

Brings the stack up fresh, runs a healthy Gatling pass and a degraded (induced-failure) Gatling
pass, and asserts — via each system's own HTTP API, not just that containers started — that
Prometheus has scraped real metrics from both services (including DB-query and circuit-breaker
series), Grafana's datasource and dashboard are provisioned and resolve real data, and
Elasticsearch has indexed structured logs including the degraded pass's induced-failure log. Tears
the stack down afterward.

### 6. Tear down

```
docker compose --profile observability down -v
```

See `conductor/tech-stack.md`'s "Local Observability Stack" section for how this is built
(containerization, compose profile gating, JSON logging, Filebeat log flattening) and its design
tradeoffs.

## sbt project compiled with Scala 3

### Usage

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

For more information on the sbt-dotty plugin, see the
[scala3-example-project](https://github.com/scala/scala3-example-project/blob/main/README.md).

### Running order-service locally (without the observability stack)

`order-service` persists orders to PostgreSQL. Start a local database first:

```
docker compose up -d
```

Then run the service as usual, e.g. `sbt "order-service/run"` or `./scripts/archive/run-services.sh`
to start both services together. `application.conf` defaults match the compose file
(`localhost:5432`, db/user/password `orders`) — no manual schema setup is needed, Flyway
migrations run automatically on startup.
