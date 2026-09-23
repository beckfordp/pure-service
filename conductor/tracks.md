# Project Tracks

This file tracks all major tracks for the project.

- [ ] **Track: Add a runtime-adjustable induced-failure endpoint to inventory-service**
  *Link: [./tracks/induced-failure-endpoint_20260923/](./tracks/induced-failure-endpoint_20260923/)*

---

## Backlog

Title-only placeholders for future tracks — not yet detailed (no spec/plan, no linked
folder), so `/conductor:implement` cannot pick these up by accident. Reorder freely as
priorities change. When ready to work on one, run `/conductor:newTrack <title>` to go
through the spec/plan questions and promote it into a real track below.

- Drive a Gatling scenario that ramps inventory-service's induced-failure rate through a sequence of values, and add Grafana panels (e.g. circuit-breaker time-to-recovery, retry success rate) that validate the resilience config's actual effectiveness, not just activity
- Add a tag-triggered CI release pipeline that publishes purerest to a real package repository (not just local Ivy2)
- Add Scaladoc to purerest's public API, published alongside releases
- Review archived `scripts/` and recover/rename or delete each based on genuine ongoing use

---
