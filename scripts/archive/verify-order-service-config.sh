#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 1 of the persistence track: confirms order-service
# boots using PureConfig-loaded application.conf defaults, and that the
# ORDER_SERVICE_PORT env-var override still works (application.conf's
# `port = ${?ORDER_SERVICE_PORT}` substitution), so run-services.sh keeps working
# unchanged.
#
# Usage: ./scripts/archive/verify-order-service-config.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

FAILED=0

wait_ready() {
  local port="$1"
  local label="$2"
  local log_file="$3"
  echo "Waiting for ${label} to become ready on port ${port}..."
  for _ in $(seq 1 60); do
    local status
    status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${port}/nope" || true)"
    if [ "$status" != "000" ]; then
      return 0
    fi
    sleep 1
  done
  echo "${label} did not become ready within 60s." >&2
  echo "--- captured output ---"
  cat "$log_file"
  return 1
}

run_and_check() {
  local port="$1"
  local env_override="$2"
  local label="$3"
  local log_file
  log_file="$(mktemp -t order-service-config-verify)"

  echo
  echo "Starting order-service (${label}, expecting port ${port})..."
  if [ -n "$env_override" ]; then
    env "$env_override" sbt --no-server "orderService/bgRun" "shell" >"$log_file" 2>&1 </dev/null &
  else
    sbt --no-server "orderService/bgRun" "shell" >"$log_file" 2>&1 </dev/null &
  fi
  local sbt_pid=$!

  cleanup() {
    kill "$sbt_pid" >/dev/null 2>&1 || true
    wait "$sbt_pid" 2>/dev/null || true
    local stale_pids
    stale_pids="$(lsof -ti "tcp:${port}" 2>/dev/null || true)"
    if [ -n "$stale_pids" ]; then
      echo "$stale_pids" | xargs kill >/dev/null 2>&1 || true
    fi
  }
  trap cleanup RETURN

  if ! wait_ready "$port" "order-service" "$log_file"; then
    FAILED=1
    return
  fi

  echo "   OK: order-service is bound to port ${port} (${label})"
}

echo "1. Default config (application.conf's port = 8080)..."
run_and_check 8080 "" "default"

echo
echo "2. ORDER_SERVICE_PORT env override (mirrors run-services.sh's usage)..."
run_and_check 8099 "ORDER_SERVICE_PORT=8099" "env override"

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
