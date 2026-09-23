#!/usr/bin/env bash
set -euo pipefail

# Runs the Gatling load test (modules/load-test) twice against a real
# order-service/inventory-service pair, to generate realistic RED metrics
# under healthy sustained traffic and realistic resilience metrics
# (retry/circuit-breaker) under a moderate induced-failure rate — the kind of
# traffic manual curl-based verify-*.sh scripts can't produce.
#
# Usage: ./scripts/loadtest-purerest.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

INVENTORY_PORT=8081
ORDER_PORT=8080
ORDER_METRICS_PORT=9090
FAILED=0
SBT_PID=""

cleanup() {
  echo
  echo "Cleaning up..."
  [ -n "$SBT_PID" ] && { kill "$SBT_PID" >/dev/null 2>&1 || true; wait "$SBT_PID" 2>/dev/null || true; }
  for port in "$INVENTORY_PORT" "$ORDER_PORT" "$ORDER_METRICS_PORT"; do
    pids="$(lsof -ti "tcp:${port}" 2>/dev/null || true)"
    [ -n "$pids" ] && echo "$pids" | xargs kill >/dev/null 2>&1 || true
  done
  docker compose down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

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

# Sums the values of all series (across labels) for a given Prometheus metric name.
sum_metric() {
  local name="$1"
  echo "$2" | awk -v m="^${name}" '$0 ~ m && $0 !~ /^#/ { sum += $NF } END { print sum + 0 }'
}

start_services() {
  local failure_rate="$1"
  local log_file
  log_file="$(mktemp -t loadtest-purerest-services)"
  INVENTORY_SERVICE_BASE_URL="http://localhost:${INVENTORY_PORT}" \
    INVENTORY_INDUCED_FAILURE_RATE="$failure_rate" \
    sbt --no-server "inventoryService/bgRun" "orderService/bgRun" "shell" >"$log_file" 2>&1 </dev/null &
  SBT_PID=$!
  if ! wait_ready "$INVENTORY_PORT" "inventory-service" || ! wait_ready "$ORDER_PORT" "order-service"; then
    cat "$log_file"
    exit 1
  fi
}

stop_services() {
  [ -n "$SBT_PID" ] && { kill "$SBT_PID" >/dev/null 2>&1 || true; wait "$SBT_PID" 2>/dev/null || true; }
  SBT_PID=""
  for port in "$INVENTORY_PORT" "$ORDER_PORT" "$ORDER_METRICS_PORT"; do
    pids="$(lsof -ti "tcp:${port}" 2>/dev/null || true)"
    [ -n "$pids" ] && echo "$pids" | xargs kill >/dev/null 2>&1 || true
  done
  sleep 1
}

echo "1. docker compose up -d (fresh volume)..."
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d
for _ in $(seq 1 60); do
  pg_status="$(docker compose ps --format '{{.Health}}' postgres 2>/dev/null || true)"
  [ "$pg_status" = "healthy" ] && break
  sleep 1
done
echo "   OK: Postgres is healthy"

echo
echo "2. PASS 1/2 — healthy load (INVENTORY_INDUCED_FAILURE_RATE=0)..."
start_services "0"
echo "   OK: both services are up"
echo "   Running Gatling (this takes ~90s)..."
if ! sbt -batch "loadTest/Gatling/test"; then
  echo "   FAIL: Gatling simulation failed under healthy load" >&2
  FAILED=1
fi
HEALTHY_METRICS="$(curl -s "http://localhost:${ORDER_METRICS_PORT}/metrics")"
server_requests="$(sum_metric 'http_server_request_duration_seconds_count' "$HEALTHY_METRICS")"
client_requests="$(sum_metric 'http_client_request_duration_seconds_count' "$HEALTHY_METRICS")"
echo "   http_server_request_duration_seconds_count = ${server_requests}"
echo "   http_client_request_duration_seconds_count = ${client_requests}"
if [ "$(echo "$server_requests > 0" | bc)" != "1" ]; then
  echo "   FAIL: expected a nonzero request count from the healthy pass" >&2
  FAILED=1
else
  echo "   OK: healthy pass produced RED metrics"
fi
stop_services

echo
echo "3. PASS 2/2 — degraded load (INVENTORY_INDUCED_FAILURE_RATE=0.3)..."
start_services "0.3"
echo "   OK: both services are up"
echo "   Running Gatling (this takes ~90s)..."
if ! sbt -batch "loadTest/Gatling/test"; then
  echo "   FAIL: Gatling simulation failed under degraded load" >&2
  FAILED=1
fi
DEGRADED_METRICS="$(curl -s "http://localhost:${ORDER_METRICS_PORT}/metrics")"
retried_total="$(sum_metric 'purerest_retry_attempts_total\{[^}]*outcome="retried"' "$DEGRADED_METRICS")"
transitions_total="$(sum_metric 'purerest_circuit_breaker_state_transitions_total' "$DEGRADED_METRICS")"
rejected_total="$(sum_metric 'purerest_circuit_breaker_calls_rejected_total' "$DEGRADED_METRICS")"
echo "   purerest_retry_attempts_total{outcome=retried} = ${retried_total}"
echo "   purerest_circuit_breaker_state_transitions_total = ${transitions_total}"
echo "   purerest_circuit_breaker_calls_rejected_total = ${rejected_total}"
if [ "$(echo "$retried_total > 0" | bc)" = "1" ]; then
  echo "   OK: degraded pass produced retry-attempt metrics"
else
  echo "   FAIL: expected nonzero purerest_retry_attempts_total{outcome=retried}" >&2
  FAILED=1
fi
if [ "$(echo "$transitions_total > 0" | bc)" = "1" ] || [ "$(echo "$rejected_total > 0" | bc)" = "1" ]; then
  echo "   OK: degraded pass produced circuit-breaker activity"
else
  echo "   FAIL: expected nonzero circuit-breaker state transitions or rejections" >&2
  FAILED=1
fi
stop_services

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
