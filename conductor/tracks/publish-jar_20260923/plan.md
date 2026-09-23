# Plan: Publish purerest as a versioned jar

## Phase 1: Publish purerest Locally
- [ ] Task: Add the `sbt-dynver` plugin to `project/plugins.sbt`; run `sbt purerest/version` to confirm it now reports a git-derived version rather than sbt's meaningless default (`0.1.0-SNAPSHOT`).
- [ ] Task: Set `purerest / organization := "io.github.beckfordp"` in `build.sbt`.
- [ ] Task: Run `sbt purerest/publishLocal`; confirm a jar now exists under `~/.ivy2/local/io.github.beckfordp/purerest_3/<version>/jars/` with the expected coordinate and dynver-derived version.
- [ ] Task: Add `scripts/verify-purerest-publish.sh` (runs `sbt purerest/publishLocal`, then inspects the local Ivy2 cache to confirm the expected coordinate/version/jar exist) and run it.
- [ ] Task: Document the new `organization`/`sbt-dynver` versioning decision in `tech-stack.md`.
- [ ] Task: Run `sbt compile` and `sbt test` across all three modules to confirm no regressions.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Publish purerest Locally' (Protocol in workflow.md)
