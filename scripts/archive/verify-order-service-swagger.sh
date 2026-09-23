#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 3 of the tapir track: starts both inventory-service
# and order-service, confirms POST /orders still works end-to-end (order-service's
# real HTTP call to inventory-service, now both served via tapir-described endpoints
# instead of hand-written HttpRoutes.of[F] pattern matches), and confirms order-service
# now also serves a browsable Swagger UI plus a generated OpenAPI yaml describing
# POST /orders.
#
# Both services are started from a single sbt session via `bgRun` — sbt refuses to
# run two independent launcher JVMs concurrently against the same build directory.
#
# Usage: ./scripts/archive/verify-order-service-swagger.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

INVENTORY_PORT="${INVENTORY_SERVICE_PORT:-8081}"
ORDER_PORT="${ORDER_SERVICE_PORT:-8080}"
LOG_FILE="$(mktemp -t services-swagger-verify)"

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

FAILED=0
ORDER_RESPONSE="$(mktemp -t order-response)"
ORDER_DOCS_UI="$(mktemp -t order-docs-ui)"
ORDER_DOCS_YAML="$(mktemp -t order-docs-yaml)"

echo
echo "1. Confirming POST /orders still works end-to-end (real call to inventory-service)..."
ORDER_STATUS="$(curl -s -o "$ORDER_RESPONSE" -w '%{http_code}' \
  -X POST "http://localhost:${ORDER_PORT}/orders" \
  -H "Content-Type: application/json" \
  -d '{"item":"widget","quantity":3}')"
if [ "$ORDER_STATUS" = "201" ] && grep -q '"item":"widget"' "$ORDER_RESPONSE" && grep -q '"reservationId"' "$ORDER_RESPONSE"; then
  echo "   OK: 201 Created with expected order + reservationId: $(cat "$ORDER_RESPONSE")"
else
  echo "   FAIL: expected 201 with a widget order + reservationId, got status ${ORDER_STATUS}: $(cat "$ORDER_RESPONSE")" >&2
  FAILED=1
fi

echo
echo "2. Confirming order-service's Swagger UI is served at /docs/..."
DOCS_STATUS="$(curl -s -L -o "$ORDER_DOCS_UI" -w '%{http_code}' "http://localhost:${ORDER_PORT}/docs/")"
if [ "$DOCS_STATUS" = "200" ] && grep -qi "swagger" "$ORDER_DOCS_UI"; then
  echo "   OK: 200, page mentions Swagger"
else
  echo "   FAIL: expected 200 Swagger UI page at /docs/, got status ${DOCS_STATUS}" >&2
  FAILED=1
fi

echo
echo "3. Confirming order-service's generated OpenAPI yaml describes POST /orders..."
YAML_STATUS="$(curl -s -o "$ORDER_DOCS_YAML" -w '%{http_code}' "http://localhost:${ORDER_PORT}/docs/docs.yaml")"
if [ "$YAML_STATUS" = "200" ] && grep -q "Order Service" "$ORDER_DOCS_YAML" && grep -q "/orders" "$ORDER_DOCS_YAML"; then
  echo "   OK: 200, spec titled 'Order Service' and describes /orders"
else
  echo "   FAIL: expected 200 OpenAPI yaml mentioning 'Order Service' and /orders, got status ${YAML_STATUS}" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above and captured output below." >&2
  echo "--- captured combined output ---"
  tail -n 80 "$LOG_FILE"
  exit 1
fi
