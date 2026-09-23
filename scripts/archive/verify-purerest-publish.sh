#!/usr/bin/env bash
set -euo pipefail

# Manual verification for Phase 1 of the publish-jar track: confirms purerest
# publishes a real, versioned jar to the local Ivy2 cache — a real Maven/Ivy
# coordinate (io.github.beckfordp) and a real git-derived version (sbt-dynver),
# not sbt's meaningless defaults ("default"/"0.1.0-SNAPSHOT").
#
# Usage: ./scripts/archive/verify-purerest-publish.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

ORG="io.github.beckfordp"
FAILED=0

echo "1. Resolving purerest's dynver-derived version..."
VERSION_LOG="$(mktemp -t verify-purerest-publish-version)"
if ! sbt -batch "purerest/version" >"$VERSION_LOG" 2>&1; then
  echo "   FAIL: 'sbt purerest/version' failed — see $VERSION_LOG" >&2
  tail -40 "$VERSION_LOG" >&2
  exit 1
fi
VERSION="$(grep -oE '^\[info\] .+$' "$VERSION_LOG" | tail -1 | sed 's/^\[info\] //')"
if [ -z "$VERSION" ] || [ "$VERSION" = "0.1.0-SNAPSHOT" ]; then
  echo "   FAIL: expected a git-derived version, got '${VERSION:-<empty>}'" >&2
  FAILED=1
else
  echo "   OK: version is git-derived: ${VERSION}"
fi

echo
echo "2. Running 'sbt purerest/publishLocal'..."
PUBLISH_LOG="$(mktemp -t verify-purerest-publish-publish)"
if sbt -batch "purerest/publishLocal" >"$PUBLISH_LOG" 2>&1; then
  echo "   OK: publishLocal succeeded"
else
  echo "   FAIL: publishLocal failed — see $PUBLISH_LOG" >&2
  tail -40 "$PUBLISH_LOG" >&2
  FAILED=1
fi

echo
echo "3. Confirming the published jar exists in the local Ivy2 cache..."
JAR_PATH="$HOME/.ivy2/local/${ORG}/purerest_3/${VERSION}/jars/purerest_3.jar"
if [ -f "$JAR_PATH" ]; then
  echo "   OK: found ${JAR_PATH}"
else
  echo "   FAIL: expected a jar at ${JAR_PATH}" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
