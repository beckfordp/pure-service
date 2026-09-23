#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 5 of the metrics track (and the track's overall
# acceptance criteria): demonstrates that retry-attempt and circuit-breaker-state
# metrics are real, populated series under genuine induced failure — not just stub
# Client[F]/Meter[F] unit tests.
#
# Reuses the resilience track's induced-failure mechanism at 100% (deterministic:
# every call fails, tripping order-service's circuit breaker — failureThreshold=5 —
# after the first order's 4 attempts plus the second order's first attempt).
#
# Usage: ./scripts/archive/verify-metrics-end-to-end.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
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

post_order() {
  curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:${ORDER_PORT}/orders" \
    -H "Content-Type: application/json" -d '{"item":"widget","quantity":1}'
}

# Sums the values of all series (across labels) for a given Prometheus metric name.
sum_metric() {
  local name="$1"
  echo "$2" | awk -v m="^${name}" '$0 ~ m && $0 !~ /^#/ { sum += $NF } END { print sum + 0 }'
}

echo "1. docker compose up -d (fresh volume)..."
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d
for _ in $(seq 1 60); do
  status="$(docker compose ps --format '{{.Health}}' postgres 2>/dev/null || true)"
  [ "$status" = "healthy" ] && break
  sleep 1
done
echo "   OK: Postgres is healthy"

echo
echo "2. Starting services with a 100% induced failure rate..."
LOG_FILE="$(mktemp -t metrics-e2e-verify)"
INVENTORY_SERVICE_BASE_URL="http://localhost:${INVENTORY_PORT}" \
  INVENTORY_INDUCED_FAILURE_RATE="1.0" \
  sbt --no-server "inventoryService/bgRun" "orderService/bgRun" "shell" >"$LOG_FILE" 2>&1 </dev/null &
SBT_PID=$!
if ! wait_ready "$INVENTORY_PORT" "inventory-service" || ! wait_ready "$ORDER_PORT" "order-service"; then
  cat "$LOG_FILE"
  exit 1
fi
echo "   OK: both services are up"

echo
echo "3. POSTing 6 orders — every attempt fails, so retries exhaust and the breaker trips..."
for _ in $(seq 1 6); do
  post_order >/dev/null
done
echo "   OK: 6 orders posted"

echo
echo "4. Checking order-service's /metrics for retry and circuit-breaker series..."
METRICS="$(curl -s "http://localhost:${ORDER_METRICS_PORT}/metrics")"

retried_total="$(sum_metric 'purerest_retry_attempts_total\{[^}]*outcome="retried"' "$METRICS")"
exhausted_total="$(sum_metric 'purerest_retry_attempts_total\{[^}]*outcome="exhausted"' "$METRICS")"
transitions_total="$(sum_metric 'purerest_circuit_breaker_state_transitions_total' "$METRICS")"
rejected_total="$(sum_metric 'purerest_circuit_breaker_calls_rejected_total' "$METRICS")"

echo "   purerest_retry_attempts_total{outcome=retried} = ${retried_total}"
echo "   purerest_retry_attempts_total{outcome=exhausted} = ${exhausted_total}"
echo "   purerest_circuit_breaker_state_transitions_total = ${transitions_total}"
echo "   purerest_circuit_breaker_calls_rejected_total = ${rejected_total}"

if [ "$(echo "$retried_total > 0" | bc)" = "1" ] && [ "$(echo "$exhausted_total > 0" | bc)" = "1" ]; then
  echo "   OK: purerest.retry.attempts has both retried and exhausted outcomes"
else
  echo "   FAIL: expected nonzero retried and exhausted retry-attempt counts" >&2
  FAILED=1
fi

if [ "$(echo "$transitions_total >= 1" | bc)" = "1" ]; then
  echo "   OK: at least one circuit-breaker state transition recorded (CLOSED -> OPEN)"
else
  echo "   FAIL: expected at least one purerest_circuit_breaker_state_transitions_total" >&2
  FAILED=1
fi

if [ "$(echo "$rejected_total > 0" | bc)" = "1" ]; then
  echo "   OK: at least one call was rejected while the breaker was open"
else
  echo "   FAIL: expected a nonzero purerest_circuit_breaker_calls_rejected_total" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
