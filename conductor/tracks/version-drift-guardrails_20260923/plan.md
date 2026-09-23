# Plan: Harden purerest version-drift guardrails (prep)

## Phase 1: Version-Drift Guardrail Prep
- [x] Task: Set `ThisBuild / evictionErrorLevel := Level.Error` in `build.sbt` (Red) and run `sbt update` to confirm it now fails on the known Skunk/otel4s-core eviction (otel4s-core 0.16.0 vs. pinned 1.1.0) — proving the new guardrail actually catches something before adding the fix. [1b60590]
- [x] Task: Add an explicit, commented `ThisBuild / dependencyOverrides += "org.typelevel" %% "otel4s-core" % otel4sVersion` pinning the eviction (Green); run `sbt update` again to confirm it now succeeds cleanly. [1b60590]
- [x] Task: Set `purerest / versionScheme := Some("early-semver")` on purerest's own project settings in `build.sbt`. [1b60590]
- [x] Task: Run `sbt compile` and `sbt test` across all three modules (purerest, order-service, inventory-service) at the new `Level.Error` setting to confirm no other evictions are silently passing today and nothing else regresses. Verified: purerest 44/44, inventoryService 10/10, orderService 21/21, all green. [1b60590]
- [x] Task: Update `tech-stack.md`'s "Transitive version drift once purerest becomes a published artifact" entry — mark options 3 and 4 as done (prep only, not yet exercised against a real external consumer), and narrow "Trigger to revisit" to cover only the actual extraction/publishing step plus the still-outstanding options 1 (API narrowing) and 2 (MiMa). [461a559]
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Version-Drift Guardrail Prep' (Protocol in workflow.md)
