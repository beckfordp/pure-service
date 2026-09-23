#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 1 of the order-reservations track: starts a throwaway
# Postgres, boots order-service, confirms both Flyway migrations apply (V1 orders table
# + V2 reservation columns), and confirms the orders table has the expected schema.
#
# Usage: ./scripts/archive/verify-order-reservations-schema.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PG_CONTAINER="verify-order-reservations-schema-pg"
ORDER_PORT=8080
LOG_FILE="$(mktemp -t order-reservations-schema-verify)"
FAILED=0

cleanup() {
  echo
  echo "Cleaning up..."
  [ -n "${SBT_PID:-}" ] && { kill "$SBT_PID" >/dev/null 2>&1 || true; wait "$SBT_PID" 2>/dev/null || true; }
  stale_pids="$(lsof -ti "tcp:${ORDER_PORT}" 2>/dev/null || true)"
  [ -n "$stale_pids" ] && echo "$stale_pids" | xargs kill >/dev/null 2>&1 || true
  docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

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
echo "2. Starting order-service..."
sbt --no-server "orderService/bgRun" "shell" >"$LOG_FILE" 2>&1 </dev/null &
SBT_PID=$!
for _ in $(seq 1 90); do
  status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${ORDER_PORT}/nope" || true)"
  [ "$status" != "000" ] && break
  sleep 1
done
if [ "$status" = "000" ]; then
  echo "   FAIL: order-service did not become ready." >&2
  cat "$LOG_FILE"
  exit 1
fi

if grep -q "Successfully applied 2 migrations" "$LOG_FILE"; then
  echo "   OK: both Flyway migrations (V1 + V2) applied"
else
  echo "   FAIL: expected 'Successfully applied 2 migrations' in the log." >&2
  FAILED=1
fi

echo
echo "3. Confirming the orders table has the reservation columns..."
COLUMNS="$(docker exec "$PG_CONTAINER" psql -U orders -d orders -t -c \
  "select column_name from information_schema.columns where table_name = 'orders' order by ordinal_position" \
  | tr -d ' ' | grep -v '^$' | paste -sd, -)"
EXPECTED="id,item,quantity,created_at,reservation_id,reserved_quantity,status"
if [ "$COLUMNS" = "$EXPECTED" ]; then
  echo "   OK: orders table has expected columns ($COLUMNS)"
else
  echo "   FAIL: expected columns $EXPECTED, got: $COLUMNS" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
