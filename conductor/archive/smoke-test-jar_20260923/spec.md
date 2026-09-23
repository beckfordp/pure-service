# Spec: Smoke-test purerest consumption as an external published jar

## Overview
Prove that a real consumer — not this repo's own `.dependsOn(purerest)` project reference — can pull purerest from the local Ivy2 cache as an ordinary `libraryDependencies` entry and actually use it at runtime. Adds a genuinely standalone sbt project (`smoke-test/`, its own build root, not part of the aggregate `build.sbt`) that depends on purerest purely as a published artifact, committed to this repo and re-run via a verification script whenever purerest changes.

## Functional Requirements
1. Create `smoke-test/` at the repo root as an independent sbt build (own `build.sbt`, `project/build.properties`, `project/plugins.sbt` if needed) — not referenced anywhere in the root `build.sbt`, so it is never swept into the main multi-project build.
2. `smoke-test/build.sbt` depends on purerest via `libraryDependencies += "io.github.beckfordp" %% "purerest" % purerestVersion`, where `purerestVersion` is read from a system property (`-DpurerestVersion=...`) passed in at invocation time — never hardcoded, since purerest's dynver-derived version changes on every commit.
3. `smoke-test` contains one real runtime exercise (not just a compile check): use purerest's `Metrics.test` in-memory testkit plus `ServerMetrics.middleware` to wrap a stub `HttpRoutes[F]`, run one request through it, and assert a `http.server.request.duration` measurement was recorded — proving the published jar's actual classes/resources work at runtime, self-contained (no external services/docker needed).
4. Add `scripts/verify-purerest-consumption.sh` (matching this repo's existing `scripts/verify-*.sh` pattern): runs `sbt purerest/publishLocal` in the main repo, resolves the current version via `sbt purerest/version`, then runs `sbt -DpurerestVersion=<version> test` inside `smoke-test/` and reports pass/fail.
5. Document `smoke-test/`'s existence and purpose in `tech-stack.md`'s Publishing section.

## Non-Functional Requirements
- `smoke-test/` must never be picked up by the root build's `sbt compile`/`sbt test` — verified by confirming the root build's project list is unchanged.
- No change to `order-service`/`inventory-service` — they keep consuming purerest via `.dependsOn(purerest)`.
- Full existing test suite (purerest/order-service/inventory-service) stays green.

## Acceptance Criteria
- `scripts/verify-purerest-consumption.sh` passes: publishes purerest, then `smoke-test/`'s own `sbt test` resolves purerest from `~/.ivy2/local` (not a project reference) and the runtime-exercise test passes.
- `sbt projects` at the repo root does not list a `smoke-test` project.
- `tech-stack.md` documents `smoke-test/`'s purpose and how to run it.

## Out of Scope
- Publishing to any remote repository.
- Testing purerest's resilience/tracing/logging APIs from the smoke-test — one representative runtime exercise (metrics) is enough to prove the consumption path works.
- CI automation running this on every commit.
