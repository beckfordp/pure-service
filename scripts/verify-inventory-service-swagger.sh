#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 2 of the tapir track: starts inventory-service,
# confirms POST /inventory/reserve still behaves exactly as before (now served
# via a tapir-described endpoint instead of a hand-written HttpRoutes.of[F]
# pattern match), and confirms the service now also serves a browsable Swagger
# UI plus a generated OpenAPI yaml describing that same endpoint.
#
# Usage: ./scripts/verify-inventory-service-swagger.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT="${INVENTORY_SERVICE_PORT:-8081}"
LOG_FILE="$(mktemp -t inventory-service-swagger-verify)"

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

FAILED=0
RESERVE_RESPONSE="$(mktemp -t inventory-reserve-response)"
DOCS_UI="$(mktemp -t inventory-docs-ui)"
DOCS_YAML="$(mktemp -t inventory-docs-yaml)"

echo
echo "1. Confirming POST /inventory/reserve still behaves as before..."
RESERVE_STATUS="$(curl -s -o "$RESERVE_RESPONSE" -w '%{http_code}' \
  -X POST "http://localhost:${PORT}/inventory/reserve" \
  -H "Content-Type: application/json" \
  -d '{"item":"widget","quantity":2}')"
if [ "$RESERVE_STATUS" = "201" ] && grep -q '"item":"widget"' "$RESERVE_RESPONSE"; then
  echo "   OK: 201 Created with expected reservation body: $(cat "$RESERVE_RESPONSE")"
else
  echo "   FAIL: expected 201 with a widget reservation, got status ${RESERVE_STATUS}: $(cat "$RESERVE_RESPONSE")" >&2
  FAILED=1
fi

echo
echo "2. Confirming the Swagger UI is served at /docs/..."
DOCS_STATUS="$(curl -s -L -o "$DOCS_UI" -w '%{http_code}' "http://localhost:${PORT}/docs/")"
if [ "$DOCS_STATUS" = "200" ] && grep -qi "swagger" "$DOCS_UI"; then
  echo "   OK: 200, page mentions Swagger"
else
  echo "   FAIL: expected 200 Swagger UI page at /docs/, got status ${DOCS_STATUS}" >&2
  FAILED=1
fi

echo
echo "3. Confirming the generated OpenAPI yaml describes the real endpoint..."
YAML_STATUS="$(curl -s -o "$DOCS_YAML" -w '%{http_code}' "http://localhost:${PORT}/docs/docs.yaml")"
if [ "$YAML_STATUS" = "200" ] && grep -q "Inventory Service" "$DOCS_YAML" && grep -q "/inventory/reserve" "$DOCS_YAML"; then
  echo "   OK: 200, spec titled 'Inventory Service' and describes /inventory/reserve"
else
  echo "   FAIL: expected 200 OpenAPI yaml mentioning 'Inventory Service' and /inventory/reserve, got status ${YAML_STATUS}" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above and captured output below." >&2
  echo "--- captured service output ---"
  tail -n 60 "$LOG_FILE"
  exit 1
fi
