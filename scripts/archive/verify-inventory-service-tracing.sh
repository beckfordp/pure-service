#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 4 of the tracing-and-logging track:
# starts inventory-service, sends a POST /inventory/reserve request, and
# prints the HTTP response plus the service's own console output so you can
# confirm the request produced both a printed span (console exporter) and a
# trace-correlated log line sharing the same trace/span id.
#
# Usage: ./scripts/archive/verify-inventory-service-tracing.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PORT="${INVENTORY_SERVICE_PORT:-8081}"
LOG_FILE="$(mktemp -t inventory-service-verify)"

echo "Starting inventory-service (sbt inventoryService/run) on port ${PORT}..."
echo "Full output is being captured to: ${LOG_FILE}"
sbt --no-server "inventoryService/run" >"$LOG_FILE" 2>&1 &
SBT_PID=$!

cleanup() {
  echo
  echo "Stopping inventory-service (pid ${SBT_PID})..."
  kill "$SBT_PID" >/dev/null 2>&1 || true
  wait "$SBT_PID" 2>/dev/null || true
}
trap cleanup EXIT

echo "Waiting for inventory-service to become ready..."
READY=0
for _ in $(seq 1 60); do
  STATUS="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/nope" || true)"
  if [ "$STATUS" != "000" ]; then
    READY=1
    break
  fi
  sleep 1
done

if [ "$READY" -ne 1 ]; then
  echo "inventory-service did not become ready within 60s." >&2
  echo "--- captured output ---"
  cat "$LOG_FILE"
  exit 1
fi

echo "inventory-service is up. Sending POST /inventory/reserve..."
echo

curl -i -X POST "http://localhost:${PORT}/inventory/reserve" \
  -H "Content-Type: application/json" \
  -d '{"item":"widget","quantity":2}'

echo
echo

# Give the async span/log output a moment to flush before we read it back.
sleep 1

echo "Check above for: HTTP/1.1 201 Created + the reservation JSON body."
echo "Check below for: a printed span for 'POST /inventory/reserve' and a log"
echo "line ('reserved 2 x widget ...'), sharing the same trace/span id:"
echo
echo "--- recent inventory-service output ---"
tail -n 40 "$LOG_FILE"
