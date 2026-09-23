#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 5 of the tracing-and-logging track: starts
# both inventory-service and order-service, sends a real POST /orders
# request, and prints both services' console output so you can confirm the
# spans printed by each service share the same trace id — proving the trace
# actually propagates across the real HTTP hop between them, not just in the
# automated in-memory test (OrderServiceTraceContinuitySuite).
#
# Both services are started from a single sbt session via `bgRun` — sbt
# refuses to run two independent launcher JVMs concurrently against the same
# build directory (a boot-lock/server-socket collision), so bgRun (sbt's own
# mechanism for running multiple apps from one build) is required here.
#
# Usage: ./scripts/archive/verify-order-service-trace-continuity.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

INVENTORY_PORT="${INVENTORY_SERVICE_PORT:-8081}"
ORDER_PORT="${ORDER_SERVICE_PORT:-8080}"
LOG_FILE="$(mktemp -t services-verify)"

echo "Starting inventory-service and order-service (single sbt session, bgRun)..."
echo "Full output is being captured to: ${LOG_FILE}"
INVENTORY_SERVICE_BASE_URL="http://localhost:${INVENTORY_PORT}" \
  sbt --no-server \
  "inventoryService/bgRun" \
  "orderService/bgRun" \
  "shell" \
  >"$LOG_FILE" 2>&1 </dev/null &
SBT_PID=$!

cleanup() {
  echo
  echo "Stopping services..."
  kill "$SBT_PID" >/dev/null 2>&1 || true
  wait "$SBT_PID" 2>/dev/null || true
  # bgRun spawns detached JVMs that may outlive the parent sbt process —
  # clean up anything still bound to either port.
  for port in "$INVENTORY_PORT" "$ORDER_PORT"; do
    local_pids="$(lsof -ti "tcp:${port}" 2>/dev/null || true)"
    if [ -n "$local_pids" ]; then
      echo "$local_pids" | xargs kill >/dev/null 2>&1 || true
    fi
  done
}
trap cleanup EXIT

wait_ready() {
  local port="$1"
  local label="$2"
  echo "Waiting for ${label} to become ready on port ${port}..."
  for _ in $(seq 1 90); do
    local status
    status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${port}/nope" || true)"
    if [ "$status" != "000" ]; then
      return 0
    fi
    sleep 1
  done
  echo "${label} did not become ready within 90s." >&2
  echo "--- captured output ---"
  cat "$LOG_FILE"
  return 1
}

wait_ready "$INVENTORY_PORT" "inventory-service"
wait_ready "$ORDER_PORT" "order-service"

echo
echo "Both services are up. Sending POST /orders..."
echo

curl -i -X POST "http://localhost:${ORDER_PORT}/orders" \
  -H "Content-Type: application/json" \
  -d '{"item":"widget","quantity":3}'

echo
echo

# Give the async span/log output a moment to flush before we read it back.
sleep 1

echo "Check above for: HTTP/1.1 201 Created + order body including a reservationId."
echo "Check below for: a 'POST /orders' span from order-service and a"
echo "'POST /inventory/reserve' span from inventory-service, sharing the same"
echo "trace id (the first hex id in each printed span line):"
echo
echo "--- recent combined output ---"
tail -n 60 "$LOG_FILE"
