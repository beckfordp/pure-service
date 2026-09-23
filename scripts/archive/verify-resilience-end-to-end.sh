#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 4 of the resilience track (and the track's overall
# acceptance criteria): demonstrates purerest's retry + circuit breaker against a
# genuinely flaky inventory-service, not just stub Client[F] unit tests.
#
# Part 1: with a moderate induced failure rate, POST /orders still succeeds most of
# the time thanks to retry masking transient failures.
# Part 2: with inventory-service failing on every call, repeated POST /orders show
# the circuit breaker kick in — early requests take the full retry+backoff time
# (~700ms, exhausting all 4 attempts), later requests fail almost instantly once the
# breaker opens and starts rejecting calls before they ever reach the network.
#
# Usage: ./scripts/archive/verify-resilience-end-to-end.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

INVENTORY_PORT=8081
ORDER_PORT=8080
FAILED=0
SBT_PID=""

cleanup() {
  echo
  echo "Cleaning up..."
  [ -n "$SBT_PID" ] && { kill "$SBT_PID" >/dev/null 2>&1 || true; wait "$SBT_PID" 2>/dev/null || true; }
  for port in "$INVENTORY_PORT" "$ORDER_PORT"; do
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

start_services() {
  local failure_rate="$1"
  LOG_FILE="$(mktemp -t resilience-e2e-verify)"
  INVENTORY_SERVICE_BASE_URL="http://localhost:${INVENTORY_PORT}" \
    INVENTORY_INDUCED_FAILURE_RATE="$failure_rate" \
    sbt --no-server "inventoryService/bgRun" "orderService/bgRun" "shell" >"$LOG_FILE" 2>&1 </dev/null &
  SBT_PID=$!
  if ! wait_ready "$INVENTORY_PORT" "inventory-service" || ! wait_ready "$ORDER_PORT" "order-service"; then
    cat "$LOG_FILE"
    exit 1
  fi
}

stop_services() {
  [ -n "$SBT_PID" ] && { kill "$SBT_PID" >/dev/null 2>&1 || true; wait "$SBT_PID" 2>/dev/null || true; }
  SBT_PID=""
  for port in "$INVENTORY_PORT" "$ORDER_PORT"; do
    pids="$(lsof -ti "tcp:${port}" 2>/dev/null || true)"
    [ -n "$pids" ] && echo "$pids" | xargs kill >/dev/null 2>&1 || true
  done
  sleep 1
}

post_order() {
  curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:${ORDER_PORT}/orders" \
    -H "Content-Type: application/json" -d '{"item":"widget","quantity":1}'
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
echo "2. Starting services with a moderate (30%) induced failure rate..."
# Note: kept deliberately below the circuit breaker's failureThreshold=5 —
# order-service's ResilienceConfig gives each order up to 4 attempts (1 + 3
# retries), so a run of ~5 consecutive failures (needed to trip the breaker) is
# already unlikely at 30% (P(one order exhausts all 4 attempts) = 0.3^4 ≈ 0.8%,
# vs. ≈13% at 60% — which is what made the first version of this check flaky: a
# tripped breaker starts rejecting everything for its whole 30s resetTimeout,
# which swamps this quick demo and has nothing to do with retry's own behavior).
start_services "0.3"
echo "   OK: both services are up"

echo
echo "3. POSTing 10 orders — retry should mask almost all transient failures..."
successes=0
for _ in $(seq 1 10); do
  status="$(post_order)"
  [ "$status" = "201" ] && successes=$((successes + 1))
done
echo "   ${successes}/10 orders succeeded despite a 30% per-attempt failure rate"
if [ "$successes" -ge 9 ]; then
  echo "   OK: retry is masking almost all transient failures"
else
  echo "   FAIL: expected at least 9/10 successes with retry active, got ${successes}/10" >&2
  FAILED=1
fi

echo
echo "4. Restarting with a 100% induced failure rate to demonstrate the circuit breaker..."
stop_services
start_services "1.0"
echo "   OK: both services are up"

echo
echo "5. POSTing 5 orders — expect early requests to take the full retry+backoff time,"
echo "   later requests to fail fast once the circuit breaker opens..."
first_ms=0
last_ms=0
for i in $(seq 1 5); do
  start_ns=$(date +%s%N)
  post_order >/dev/null
  end_ns=$(date +%s%N)
  ms=$(( (end_ns - start_ns) / 1000000 ))
  echo "   request #${i}: ${ms}ms"
  [ "$i" -eq 1 ] && first_ms=$ms
  last_ms=$ms
done
echo "   first request: ${first_ms}ms, last request: ${last_ms}ms"
if [ "$last_ms" -lt "$first_ms" ] && [ "$last_ms" -lt 300 ]; then
  echo "   OK: later requests fail fast (circuit breaker is open) — much faster than the first request's full retry sequence"
else
  echo "   FAIL: expected the last request to be both faster than the first and under 300ms (breaker should be open by then)" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
