#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 3 of the resilience track: confirms wiring
# Resilience.middleware into order-service's InventoryClient doesn't regress the
# normal (healthy inventory-service) happy path. Genuine retry/circuit-breaker
# behavior under induced failure is verified in Phase 4, once inventory-service can
# be made deliberately flaky.
#
# Usage: ./scripts/archive/verify-resilience-wiring.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

INVENTORY_PORT=8081
ORDER_PORT=8080
FAILED=0

cleanup() {
  echo
  echo "Cleaning up..."
  [ -n "${SBT_PID:-}" ] && { kill "$SBT_PID" >/dev/null 2>&1 || true; wait "$SBT_PID" 2>/dev/null || true; }
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
echo "2. Starting inventory-service and order-service (now with resilient client)..."
LOG_FILE="$(mktemp -t resilience-wiring-verify)"
INVENTORY_SERVICE_BASE_URL="http://localhost:${INVENTORY_PORT}" \
  sbt --no-server "inventoryService/bgRun" "orderService/bgRun" "shell" >"$LOG_FILE" 2>&1 </dev/null &
SBT_PID=$!
if ! wait_ready "$INVENTORY_PORT" "inventory-service" || ! wait_ready "$ORDER_PORT" "order-service"; then
  cat "$LOG_FILE"
  exit 1
fi
echo "   OK: both services are up"

echo
echo "3. POST /orders (healthy inventory-service — no regression expected)..."
RESPONSE="$(mktemp -t resilience-wiring-order-response)"
STATUS="$(curl -s -o "$RESPONSE" -w '%{http_code}' -X POST "http://localhost:${ORDER_PORT}/orders" \
  -H "Content-Type: application/json" -d '{"item":"widget","quantity":1}')"
if [ "$STATUS" = "201" ] && grep -q '"reservationId"' "$RESPONSE"; then
  echo "   OK: 201 Created with reservationId: $(cat "$RESPONSE")"
else
  echo "   FAIL: expected 201 with reservationId, got status ${STATUS}: $(cat "$RESPONSE")" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
