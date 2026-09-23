#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 5 (and the overall acceptance criteria) of the
# observability-stack track: brings up the full `--profile observability` compose
# stack, drives real traffic through it with the Gatling load-test module (a
# healthy pass, then a degraded pass that induces retries/circuit-breaker activity
# and a WARN-level induced-failure log), and confirms Prometheus, Grafana, and
# Elasticsearch are all populated with real data from that traffic — not just that
# the containers started.
#
# Usage: ./scripts/verify-observability-stack.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ORDER_METRICS_PORT=9092  # Prometheus's own host port (see docker-compose.yml)
FAILED=0

cleanup() {
  echo
  echo "Cleaning up (tearing down the observability profile)..."
  docker compose --profile observability down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_healthy() {
  local service="$1"
  for _ in $(seq 1 90); do
    local status
    status="$(docker compose ps --format '{{.Health}}' "$service" 2>/dev/null || true)"
    [ "$status" = "healthy" ] && return 0
    sleep 1
  done
  echo "FAIL: ${service} did not become healthy in time." >&2
  return 1
}

wait_ready() {
  local port="$1"
  local label="$2"
  for _ in $(seq 1 90); do
    local status
    status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${port}/nope" || true)"
    [ "$status" != "000" ] && return 0
    sleep 1
  done
  echo "FAIL: ${label} did not become ready in time." >&2
  return 1
}

# Sums the values of all series (across labels) for a given Prometheus metric name,
# queried through Prometheus's own HTTP API (not a raw /metrics scrape) so this also
# exercises the same query path Grafana's dashboard uses.
prom_query_sum() {
  local expr="$1"
  curl -s -G "http://localhost:${ORDER_METRICS_PORT}/api/v1/query" --data-urlencode "query=${expr}" \
    | python3 -c "
import json, sys
d = json.load(sys.stdin)
print(sum(float(r['value'][1]) for r in d['data']['result']))
"
}

gt_zero() {
  [ "$(echo "$1 > 0" | bc)" = "1" ]
}

echo "1. docker compose --profile observability up -d (fresh volumes)..."
docker compose --profile observability down -v >/dev/null 2>&1 || true
docker compose --profile observability up -d
wait_healthy postgres
wait_healthy elasticsearch
wait_healthy kibana
wait_ready 8080 "order-service"
wait_ready 8081 "inventory-service"
echo "   OK: postgres/elasticsearch/kibana healthy, order-service/inventory-service ready"

echo
echo "2. Confirming Prometheus has scraped both services..."
for _ in $(seq 1 30); do
  targets="$(curl -s "http://localhost:${ORDER_METRICS_PORT}/api/v1/targets")"
  up_count="$(echo "$targets" | python3 -c "
import json, sys
d = json.load(sys.stdin)
print(sum(1 for t in d['data']['activeTargets'] if t['health'] == 'up'))
")"
  [ "$up_count" = "2" ] && break
  sleep 1
done
if [ "$up_count" = "2" ]; then
  echo "   OK: both order-service and inventory-service targets are up"
else
  echo "   FAIL: expected 2 Prometheus targets up, got ${up_count}" >&2
  echo "$targets" >&2
  FAILED=1
fi

echo
echo "3. Confirming Grafana's datasource + dashboard are provisioned..."
datasources="$(curl -s "http://localhost:3000/api/datasources")"
if echo "$datasources" | python3 -c "import json,sys; d=json.load(sys.stdin); sys.exit(0 if any(x['type']=='prometheus' for x in d) else 1)"; then
  echo "   OK: Prometheus datasource provisioned"
else
  echo "   FAIL: no Prometheus datasource found in Grafana" >&2
  FAILED=1
fi
search="$(curl -s "http://localhost:3000/api/search?query=")"
if echo "$search" | python3 -c "import json,sys; d=json.load(sys.stdin); sys.exit(0 if any(x['uid']=='purerest-red-resilience' for x in d) else 1)"; then
  echo "   OK: purerest dashboard provisioned"
else
  echo "   FAIL: purerest dashboard not found in Grafana" >&2
  FAILED=1
fi

echo
echo "3b. Confirming Kibana's purerest-logs-* Data View is provisioned..."
# kibana-setup is a one-shot that runs once kibana is healthy; give it a moment
# to complete before checking.
for _ in $(seq 1 30); do
  status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:5601/api/data_views/data_view/purerest-logs")"
  [ "$status" = "200" ] && break
  sleep 1
done
if [ "$status" = "200" ]; then
  echo "   OK: purerest-logs Data View provisioned in Kibana"
else
  echo "   FAIL: expected Kibana's purerest-logs Data View to exist (got HTTP ${status})" >&2
  FAILED=1
fi

echo
echo "4. PASS 1/2 — healthy load (INVENTORY_INDUCED_FAILURE_RATE=0)..."
echo "   Running Gatling (this takes ~90s)..."
if ! sbt -batch "loadTest/Gatling/test"; then
  echo "   FAIL: Gatling simulation failed under healthy load" >&2
  FAILED=1
fi
server_requests="$(prom_query_sum 'sum(http_server_request_duration_seconds_count)')"
db_queries="$(prom_query_sum 'sum(db_client_operation_duration_seconds_count)')"
echo "   http_server_request_duration_seconds_count = ${server_requests}"
echo "   db_client_operation_duration_seconds_count = ${db_queries}"
if gt_zero "$server_requests" && gt_zero "$db_queries"; then
  echo "   OK: healthy pass produced RED + DB-query metrics"
else
  echo "   FAIL: expected nonzero request and DB-query counts from the healthy pass" >&2
  FAILED=1
fi

echo
echo "5. PASS 2/2 — degraded load (INVENTORY_INDUCED_FAILURE_RATE=0.5)..."
# 0.5, not 0.3: the circuit breaker needs failureThreshold=5 consecutive failures to
# trip (order-service's ResilienceConfig), and with maxRetries=3 (4 attempts/request)
# a single request alone can't reach 5 — it needs a streak spanning request
# boundaries. At 0.3 that streak is a coin flip over one Gatling run (~1 expected
# occurrence); 0.5 makes it reliable (~10+ expected) while still leaving roughly half
# of attempts succeeding, so retried/exhausted/rejected outcomes all show up.
INVENTORY_INDUCED_FAILURE_RATE="0.5" \
  docker compose --profile observability up -d --force-recreate --no-deps inventory-service
wait_ready 8081 "inventory-service"
echo "   Running Gatling (this takes ~90s)..."
if ! sbt -batch "loadTest/Gatling/test"; then
  echo "   FAIL: Gatling simulation failed under degraded load" >&2
  FAILED=1
fi
retried_total="$(prom_query_sum 'sum(purerest_retry_attempts_total{outcome="retried"})')"
transitions_total="$(prom_query_sum 'sum(purerest_circuit_breaker_state_transitions_total)')"
rejected_total="$(prom_query_sum 'sum(purerest_circuit_breaker_calls_rejected_total)')"
cb_state="$(prom_query_sum 'purerest_circuit_breaker_state')"
echo "   purerest_retry_attempts_total{outcome=retried} = ${retried_total}"
echo "   purerest_circuit_breaker_state_transitions_total = ${transitions_total}"
echo "   purerest_circuit_breaker_calls_rejected_total = ${rejected_total}"
if gt_zero "$retried_total"; then
  echo "   OK: degraded pass produced retry-attempt metrics"
else
  echo "   FAIL: expected nonzero purerest_retry_attempts_total{outcome=retried}" >&2
  FAILED=1
fi
if gt_zero "$transitions_total" || gt_zero "$rejected_total"; then
  echo "   OK: degraded pass produced circuit-breaker activity"
else
  echo "   FAIL: expected nonzero circuit-breaker state transitions or rejections" >&2
  FAILED=1
fi
if [ -n "$cb_state" ]; then
  echo "   OK: live circuit-breaker state gauge is exposed"
else
  echo "   FAIL: expected a purerest_circuit_breaker_state series" >&2
  FAILED=1
fi

echo
echo "6. Confirming Elasticsearch indexed structured logs from both services,"
echo "   including an induced-failure WARN log from the degraded pass..."
# Filebeat ships on a short poll interval, but give it a moment to catch up on the
# degraded pass's tail before searching.
sleep 10
es_count="$(curl -s "http://localhost:9200/purerest-logs-*/_count" | python3 -c "import json,sys; print(json.load(sys.stdin)['count'])")"
echo "   purerest-logs-* document count = ${es_count}"
if gt_zero "$es_count"; then
  echo "   OK: Elasticsearch has indexed log documents"
else
  echo "   FAIL: expected Elasticsearch to have indexed at least one log document" >&2
  FAILED=1
fi

trace_id_hits="$(curl -s "http://localhost:9200/purerest-logs-*/_search" -H "Content-Type: application/json" -d '{
  "query": { "exists": { "field": "trace_id" } },
  "size": 0
}' | python3 -c "import json,sys; print(json.load(sys.stdin)['hits']['total']['value'])")"
echo "   docs with a trace_id field = ${trace_id_hits}"
if gt_zero "$trace_id_hits"; then
  echo "   OK: structured trace_id field is indexed and searchable"
else
  echo "   FAIL: expected at least one log document with a trace_id field" >&2
  FAILED=1
fi

induced_failure_hits="$(curl -s "http://localhost:9200/purerest-logs-*/_search" -H "Content-Type: application/json" -d '{
  "query": { "match_phrase": { "message": "Induced failure triggered" } },
  "size": 0
}' | python3 -c "import json,sys; print(json.load(sys.stdin)['hits']['total']['value'])")"
echo "   docs with induced-failure WARN message = ${induced_failure_hits}"
if gt_zero "$induced_failure_hits"; then
  echo "   OK: the induced-failure WARN log from the degraded pass is indexed"
else
  echo "   FAIL: expected at least one indexed 'Induced failure triggered' log" >&2
  FAILED=1
fi

echo
echo "Grafana:      http://localhost:3000/d/purerest-red-resilience"
echo "Kibana:       http://localhost:5601"
echo "Prometheus:   http://localhost:${ORDER_METRICS_PORT}"
echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
