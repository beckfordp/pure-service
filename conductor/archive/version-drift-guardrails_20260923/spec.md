# Spec: Harden purerest version-drift guardrails (prep)

## Overview
Prepare purerest's build configuration now, while it is still a subproject sharing one dependency-version `val` with its consumers, for the version-drift risk documented in `tech-stack.md`'s "Transitive version drift once purerest becomes a published artifact" deferred concern — so that when purerest is eventually extracted and published, its guardrails (real versionScheme metadata + fail-fast eviction handling) are already in place and already exercised, not bolted on afterward. Also resolves the build's one existing blanket eviction suppression with an explicit, intentional override.

## Functional Requirements
1. Set `purerest / versionScheme := Some("early-semver")` on purerest's own project settings in `build.sbt`.
2. Change `ThisBuild / evictionErrorLevel` from `Level.Warn` to `Level.Error`.
3. Replace the blanket `Level.Warn` suppression's effect on the known Skunk/otel4s-core eviction (Skunk 1.0.0's optional `otel4s-core` 0.16.0 dependency vs. this build's pinned otel4s 1.1.0) with an explicit `ThisBuild / dependencyOverrides += ...` pinning `otel4s-core` to 1.1.0, documented with a comment explaining why.
4. Verify `sbt update`/`compile`/`test` across all three modules succeeds cleanly at `Level.Error` with only that one documented override in place — confirming no other evictions are silently passing today.
5. Update `tech-stack.md`'s deferred-concern entry: mark options 3 and 4 as done (prep only — not yet exercised against a real external consumer, since purerest isn't published), and narrow the "Trigger to revisit" note to cover only the actual extraction/publishing step plus options 1 (API narrowing) and 2 (MiMa), which remain outstanding.

## Non-Functional Requirements
- No runtime behavior change to any service — purely build-time/dependency-resolution configuration.
- Full existing test suite (purerest, order-service, inventory-service) stays green.

## Acceptance Criteria
- `sbt compile`/`test` succeeds across all modules with `evictionErrorLevel := Level.Error`.
- The Skunk/otel4s-core eviction is resolved via an explicit, commented `dependencyOverrides` entry, not Warn-level suppression.
- `purerest`'s `build.sbt` declares `versionScheme := Some("early-semver")`, scoped to the purerest project only.
- `tech-stack.md`'s deferred-concern entry reflects options 3+4 as done (prep), with the revisit trigger narrowed accordingly.

## Out of Scope
- Actually extracting purerest into its own build/repository or publishing it anywhere.
- Adopting MiMa (option 2) or narrowing purerest's public API (option 1).
- Any other unrelated dependency version bumps.
