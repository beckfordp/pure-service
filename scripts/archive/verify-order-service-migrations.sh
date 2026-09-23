#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 2 of the persistence track: starts a throwaway Postgres
# container matching application.conf's defaults, boots order-service against it, and
# confirms Flyway applied the orders-table migration on startup.
#
# Usage: ./scripts/archive/verify-order-service-migrations.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PG_CONTAINER="verify-order-service-migrations-pg"
ORDER_PORT=8080
LOG_FILE="$(mktemp -t order-service-migrations-verify)"
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

echo "1. Starting a throwaway Postgres container (matching application.conf defaults)..."
docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$PG_CONTAINER" -p 5432:5432 \
  -e POSTGRES_DB=orders -e POSTGRES_USER=orders -e POSTGRES_PASSWORD=orders \
  postgres:16-alpine >/dev/null

echo "   Waiting for Postgres to accept connections..."
for _ in $(seq 1 60); do
  if docker exec "$PG_CONTAINER" pg_isready -U orders >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
if ! docker exec "$PG_CONTAINER" pg_isready -U orders >/dev/null 2>&1; then
  echo "   FAIL: Postgres did not become ready in time." >&2
  exit 1
fi
echo "   OK: Postgres is ready"

echo
echo "2. Starting order-service (should run Flyway migration on startup)..."
sbt --no-server "orderService/bgRun" "shell" >"$LOG_FILE" 2>&1 </dev/null &
SBT_PID=$!

echo "   Waiting for order-service to become ready on port ${ORDER_PORT}..."
for _ in $(seq 1 90); do
  status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${ORDER_PORT}/nope" || true)"
  if [ "$status" != "000" ]; then
    break
  fi
  sleep 1
done
if [ "$status" = "000" ]; then
  echo "   FAIL: order-service did not become ready within 90s." >&2
  echo "--- captured output ---"
  cat "$LOG_FILE"
  FAILED=1
fi

if [ "$FAILED" -eq 0 ]; then
  if grep -q "Successfully applied 1 migration" "$LOG_FILE" && grep -q "create orders table" "$LOG_FILE"; then
    echo "   OK: order-service booted and Flyway applied the orders-table migration"
  else
    echo "   FAIL: expected Flyway migration log lines not found." >&2
    FAILED=1
  fi
fi

echo
echo "3. Confirming the orders table exists in Postgres..."
COLUMNS="$(docker exec "$PG_CONTAINER" psql -U orders -d orders -t -c \
  "select column_name from information_schema.columns where table_name = 'orders' order by ordinal_position" \
  | tr -d ' ' | grep -v '^$' | paste -sd, -)"
if [ "$COLUMNS" = "id,item,quantity,created_at" ]; then
  echo "   OK: orders table has expected columns ($COLUMNS)"
else
  echo "   FAIL: expected columns id,item,quantity,created_at, got: $COLUMNS" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
