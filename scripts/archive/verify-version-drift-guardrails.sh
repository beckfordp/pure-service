#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 1 of the version-drift-guardrails track: confirms
# purerest's build.sbt now fails the build on an incompatible eviction rather than
# only warning (evictionErrorLevel := Level.Error), that the one known eviction
# (Skunk's otel4s-core 0.16.0 vs. this build's pinned 1.1.0) is pinned via an
# explicit dependencyOverrides entry rather than a blanket suppression, and that
# purerest declares real versionScheme metadata for a future published jar.
#
# Usage: ./scripts/archive/verify-version-drift-guardrails.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

FAILED=0

echo "1. Confirming build.sbt declares the guardrail settings..."
if grep -q 'ThisBuild / evictionErrorLevel := Level.Error' build.sbt; then
  echo "   OK: evictionErrorLevel := Level.Error"
else
  echo "   FAIL: expected 'ThisBuild / evictionErrorLevel := Level.Error' in build.sbt" >&2
  FAILED=1
fi

if grep -q 'versionScheme := Some("early-semver")' build.sbt; then
  echo "   OK: purerest declares versionScheme := Some(\"early-semver\")"
else
  echo "   FAIL: expected 'versionScheme := Some(\"early-semver\")' in build.sbt" >&2
  FAILED=1
fi

for coord in otel4s-core otel4s-core-common otel4s-core-metrics; do
  if grep -q "\"org.typelevel\" %% \"${coord}\" % otel4sVersion" build.sbt; then
    echo "   OK: dependencyOverrides pins ${coord} to otel4sVersion"
  else
    echo "   FAIL: expected an explicit dependencyOverrides entry for ${coord}" >&2
    FAILED=1
  fi
done

echo
echo "2. Running 'sbt update' — should succeed cleanly at Level.Error with only the"
echo "   documented overrides in place (no other evictions silently passing)..."
UPDATE_LOG="$(mktemp -t verify-version-drift-guardrails-update)"
if sbt -batch "update" >"$UPDATE_LOG" 2>&1; then
  echo "   OK: sbt update succeeded"
else
  echo "   FAIL: sbt update failed — see $UPDATE_LOG" >&2
  tail -40 "$UPDATE_LOG" >&2
  FAILED=1
fi

echo
echo "3. Running the full test suite across all three modules..."
TEST_LOG="$(mktemp -t verify-version-drift-guardrails-test)"
if sbt -batch "purerest/test" "inventoryService/test" "orderService/test" \
  >"$TEST_LOG" 2>&1; then
  if grep -q "^\[info\] Passed:" "$TEST_LOG"; then
    grep "^\[info\] Passed:" "$TEST_LOG" | sed 's/^/   /'
    echo "   OK: full test suite passed"
  else
    echo "   FAIL: sbt test succeeded but no 'Passed:' summary found — inspect the log" >&2
    FAILED=1
  fi
else
  echo "   FAIL: sbt test failed — see $TEST_LOG" >&2
  tail -60 "$TEST_LOG" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
