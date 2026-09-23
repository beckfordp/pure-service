# Spec: Publish purerest as a versioned jar

## Overview
Make `purerest` publishable as a real, versioned jar artifact — locally, alongside the existing fixtures (order-service/inventory-service), without splitting it into its own repository yet. This is prep for the next backlog item (smoke-testing purerest as an external `libraryDependencies` consumer) and for the eventual real extraction, giving purerest a real Maven/Ivy coordinate and a real, git-derived version instead of sbt's meaningless defaults.

## Functional Requirements
1. Set `purerest / organization := "io.github.beckfordp"` (derived from the project's own GitHub remote, `github.com/beckfordp/pure-service`).
2. Add the `sbt-dynver` plugin to `project/plugins.sbt`; let it derive `purerest`'s `version` automatically from git tags/commits (no manual `version :=`).
3. Confirm `purerest / publishLocal` produces a real jar in the local Ivy2 cache under the `io.github.beckfordp` organization, with a real dynver-derived version and the `early-semver` versionScheme already set by the previous track.
4. Document the new `organization`/versioning choice in `tech-stack.md` (a tech-stack change per `workflow.md`'s "Tech Stack is Deliberate" principle).
5. Add a `scripts/verify-purerest-publish.sh` script (matching this project's existing verification-script pattern) that runs `sbt purerest/publishLocal`, then inspects the local Ivy2 cache to confirm the expected coordinate/version/jar exist.

## Non-Functional Requirements
- No CI/release-automation pipeline in this track — `publishLocal` stays a manual, on-demand developer action.
- No change to `order-service`/`inventory-service`'s dependency on purerest — they keep using the internal `ProjectRef` (`.dependsOn(purerest)`), not the published jar. Consuming the published jar is explicitly the next backlog item's job.
- Full existing test suite stays green (no runtime behavior change).

## Acceptance Criteria
- `sbt purerest/publishLocal` succeeds and produces a jar under `~/.ivy2/local/io.github.beckfordp/purerest_3/<dynver-version>/`.
- The published version reflects the current git state (e.g. `0.1.0-SNAPSHOT`-style or a dynver-formatted string) rather than a hardcoded literal.
- `tech-stack.md` documents the organization/versioning decision.
- `scripts/verify-purerest-publish.sh` passes.

## Out of Scope
- Actually consuming the published jar from another module/build (next backlog item).
- Publishing to any remote/real repository (Sonatype, GitHub Packages, etc.).
- Extracting purerest into its own repository.
- CI-driven or tag-triggered publishing.
