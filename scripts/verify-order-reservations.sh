#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 3 of the order-reservations track (and the track's
# overall acceptance criteria): boots the real stack via docker-compose + both
# services, then confirms POST /orders followed by GET /orders/{id} shows the order
# with its reservation and status, and that GET /orders/{unknown-id} returns a clean
# 404 with a JSON error body.
#
# Usage: ./scripts/verify-order-reservations.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
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
  docker compose down >/dev/null 2>&1 || true
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

echo "1. docker compose up -d (fresh volume — V2's NOT NULL columns need a reset DB;"
echo "   see spec.md's 'assumes a resettable/dev-only database' note)..."
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d
for _ in $(seq 1 60); do
  status="$(docker compose ps --format '{{.Health}}' postgres 2>/dev/null || true)"
  [ "$status" = "healthy" ] && break
  sleep 1
done
echo "   OK: Postgres is healthy"

echo
echo "2. Starting inventory-service and order-service..."
LOG_FILE="$(mktemp -t order-reservations-verify)"
INVENTORY_SERVICE_BASE_URL="http://localhost:${INVENTORY_PORT}" \
  sbt --no-server "inventoryService/bgRun" "orderService/bgRun" "shell" >"$LOG_FILE" 2>&1 </dev/null &
SBT_PID=$!
if ! wait_ready "$INVENTORY_PORT" "inventory-service" || ! wait_ready "$ORDER_PORT" "order-service"; then
  cat "$LOG_FILE"
  exit 1
fi
echo "   OK: both services are up"

echo
echo "3. POST /orders..."
POST_RESPONSE="$(mktemp -t order-reservations-post-response)"
POST_STATUS="$(curl -s -o "$POST_RESPONSE" -w '%{http_code}' -X POST "http://localhost:${ORDER_PORT}/orders" \
  -H "Content-Type: application/json" -d '{"item":"widget","quantity":2}')"
ORDER_ID="$(grep -o '"id":"[^"]*"' "$POST_RESPONSE" | head -1 | cut -d'"' -f4)"
if [ "$POST_STATUS" = "201" ] && [ -n "$ORDER_ID" ]; then
  echo "   OK: 201 Created, order id ${ORDER_ID}: $(cat "$POST_RESPONSE")"
else
  echo "   FAIL: expected 201 with an id, got status ${POST_STATUS}: $(cat "$POST_RESPONSE")" >&2
  FAILED=1
fi

echo
echo "4. GET /orders/{id}..."
GET_RESPONSE="$(mktemp -t order-reservations-get-response)"
GET_STATUS="$(curl -s -o "$GET_RESPONSE" -w '%{http_code}' "http://localhost:${ORDER_PORT}/orders/${ORDER_ID}")"
if [ "$GET_STATUS" = "200" ] && grep -q '"status":"reserved"' "$GET_RESPONSE" && grep -q '"reservationId"' "$GET_RESPONSE"; then
  echo "   OK: 200 with status=reserved and a reservationId: $(cat "$GET_RESPONSE")"
else
  echo "   FAIL: expected 200 with status=reserved, got status ${GET_STATUS}: $(cat "$GET_RESPONSE")" >&2
  FAILED=1
fi

echo
echo "5. GET /orders/{unknown-id}..."
NOTFOUND_RESPONSE="$(mktemp -t order-reservations-404-response)"
NOTFOUND_STATUS="$(curl -s -o "$NOTFOUND_RESPONSE" -w '%{http_code}' "http://localhost:${ORDER_PORT}/orders/$(uuidgen)")"
if [ "$NOTFOUND_STATUS" = "404" ] && grep -q '"error"' "$NOTFOUND_RESPONSE"; then
  echo "   OK: 404 with a JSON error body: $(cat "$NOTFOUND_RESPONSE")"
else
  echo "   FAIL: expected 404 with a JSON error body, got status ${NOTFOUND_STATUS}: $(cat "$NOTFOUND_RESPONSE")" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
