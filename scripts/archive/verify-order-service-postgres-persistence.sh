#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 3 of the persistence track (and the track's overall
# acceptance criteria): confirms POST /orders persists a real row in Postgres via the
# Skunk-backed OrderStore, and that a restarted order-service does not lose previously
# created orders (Flyway migration is idempotent, data survives the process restart).
#
# Usage: ./scripts/archive/verify-order-service-postgres-persistence.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PG_CONTAINER="verify-order-service-persistence-pg"
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
  docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
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

echo "1. Starting a throwaway Postgres container..."
docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$PG_CONTAINER" -p 5432:5432 \
  -e POSTGRES_DB=orders -e POSTGRES_USER=orders -e POSTGRES_PASSWORD=orders \
  postgres:16-alpine >/dev/null
for _ in $(seq 1 60); do
  docker exec "$PG_CONTAINER" pg_isready -U orders >/dev/null 2>&1 && break
  sleep 1
done
echo "   OK: Postgres is ready"

echo
echo "2. Starting inventory-service and order-service..."
LOG_FILE="$(mktemp -t order-service-persistence-verify)"
INVENTORY_SERVICE_BASE_URL="http://localhost:${INVENTORY_PORT}" \
  sbt --no-server "inventoryService/bgRun" "orderService/bgRun" "shell" >"$LOG_FILE" 2>&1 </dev/null &
SBT_PID=$!
wait_ready "$INVENTORY_PORT" "inventory-service" || { FAILED=1; }
wait_ready "$ORDER_PORT" "order-service" || { FAILED=1; }
[ "$FAILED" -eq 1 ] && { cat "$LOG_FILE"; exit 1; }
echo "   OK: both services are up"

echo
echo "3. POST /orders (first order)..."
RESPONSE_1="$(mktemp -t order-response-1)"
STATUS_1="$(curl -s -o "$RESPONSE_1" -w '%{http_code}' -X POST "http://localhost:${ORDER_PORT}/orders" \
  -H "Content-Type: application/json" -d '{"item":"widget","quantity":3}')"
ORDER_ID_1="$(grep -o '"id":"[^"]*"' "$RESPONSE_1" | head -1 | cut -d'"' -f4)"
if [ "$STATUS_1" = "201" ] && [ -n "$ORDER_ID_1" ]; then
  echo "   OK: 201 Created, order id ${ORDER_ID_1}"
else
  echo "   FAIL: expected 201 with an id, got status ${STATUS_1}: $(cat "$RESPONSE_1")" >&2
  FAILED=1
fi

echo
echo "4. Confirming the order is queryable directly in Postgres..."
FOUND="$(docker exec "$PG_CONTAINER" psql -U orders -d orders -t -c "select count(*) from orders where id = '${ORDER_ID_1}'" | tr -d ' ')"
if [ "$FOUND" = "1" ]; then
  echo "   OK: row found in Postgres"
else
  echo "   FAIL: expected exactly 1 row for id ${ORDER_ID_1}, got: ${FOUND}" >&2
  FAILED=1
fi

echo
echo "5. Restarting order-service (stopping both services, starting order-service alone)..."
kill "$SBT_PID" >/dev/null 2>&1 || true
wait "$SBT_PID" 2>/dev/null || true
for port in "$INVENTORY_PORT" "$ORDER_PORT"; do
  pids="$(lsof -ti "tcp:${port}" 2>/dev/null || true)"
  [ -n "$pids" ] && echo "$pids" | xargs kill >/dev/null 2>&1 || true
done
sleep 2

LOG_FILE_2="$(mktemp -t order-service-persistence-verify-restart)"
sbt --no-server "orderService/bgRun" "shell" >"$LOG_FILE_2" 2>&1 </dev/null &
SBT_PID=$!
if wait_ready "$ORDER_PORT" "order-service (restarted)"; then
  if grep -q "up to date. No migration necessary" "$LOG_FILE_2"; then
    echo "   OK: order-service restarted, Flyway migration was idempotent"
  else
    echo "   FAIL: expected an idempotent-migration log line on restart." >&2
    FAILED=1
  fi
else
  cat "$LOG_FILE_2"
  FAILED=1
fi

echo
echo "6. Confirming the original order still exists after restart..."
FOUND_AFTER="$(docker exec "$PG_CONTAINER" psql -U orders -d orders -t -c "select count(*) from orders where id = '${ORDER_ID_1}'" | tr -d ' ')"
if [ "$FOUND_AFTER" = "1" ]; then
  echo "   OK: original order survived the restart"
else
  echo "   FAIL: expected the original order to still exist, got count: ${FOUND_AFTER}" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
