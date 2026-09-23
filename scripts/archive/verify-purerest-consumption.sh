#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 1 of the smoke-test-jar track: proves a genuinely
# standalone sbt build (smoke-test/, not part of the root aggregate build.sbt) can
# resolve purerest purely as a published jar — via an ordinary libraryDependencies
# entry against the local Ivy2 cache, not this repo's internal
# .dependsOn(purerest) project reference — and actually use it at runtime.
#
# Usage: ./scripts/archive/verify-purerest-consumption.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

FAILED=0

echo "1. Confirming smoke-test/ is isolated from the root aggregate build..."
PROJECTS_LOG="$(mktemp -t verify-purerest-consumption-projects)"
sbt -batch "projects" >"$PROJECTS_LOG" 2>&1
if grep -qi "smoke-test" "$PROJECTS_LOG"; then
  echo "   FAIL: expected 'sbt projects' at the repo root to not mention smoke-test" >&2
  FAILED=1
else
  echo "   OK: smoke-test is not listed in the root build's projects"
fi

echo
echo "2. Publishing purerest locally and resolving its current version..."
if ! sbt -batch "purerest/publishLocal" >"$(mktemp -t verify-purerest-consumption-publish)" 2>&1; then
  echo "   FAIL: sbt purerest/publishLocal failed" >&2
  exit 1
fi
VERSION_LOG="$(mktemp -t verify-purerest-consumption-version)"
sbt -batch "purerest/version" >"$VERSION_LOG" 2>&1
PURE_VERSION="$(grep -oE '^\[info\] .+$' "$VERSION_LOG" | tail -1 | sed 's/^\[info\] //')"
if [ -z "$PURE_VERSION" ]; then
  echo "   FAIL: could not resolve purerest's version — see $VERSION_LOG" >&2
  exit 1
fi
echo "   OK: purerest published at version ${PURE_VERSION}"

echo
echo "3. Running smoke-test/'s own test suite against the published jar..."
SMOKE_LOG="$(mktemp -t verify-purerest-consumption-smoke)"
if (cd smoke-test && sbt -batch "-DpurerestVersion=${PURE_VERSION}" "test") >"$SMOKE_LOG" 2>&1; then
  if grep -q "^\[info\] Passed:" "$SMOKE_LOG"; then
    grep "^\[info\] Passed:" "$SMOKE_LOG" | sed 's/^/   /'
    echo "   OK: smoke-test passed, resolving purerest from the local Ivy2 cache"
  else
    echo "   FAIL: smoke-test succeeded but no 'Passed:' summary found — inspect $SMOKE_LOG" >&2
    FAILED=1
  fi
else
  echo "   FAIL: smoke-test failed — see $SMOKE_LOG" >&2
  tail -40 "$SMOKE_LOG" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
