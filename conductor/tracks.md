# Project Tracks

This file tracks all major tracks for the project.

---

- [ ] **Track: Harden purerest version-drift guardrails (prep)**
  *Link: [./tracks/version-drift-guardrails_20260923/](./tracks/version-drift-guardrails_20260923/)*

---

## Backlog

Title-only placeholders for future tracks — not yet detailed (no spec/plan, no linked
folder), so `/conductor:implement` cannot pick these up by accident. Reorder freely as
priorities change. When ready to work on one, run `/conductor:newTrack <title>` to go
through the spec/plan questions and promote it into a real track below.

- Logging including trace to Kibana
- Deploy to local Kubernetes runtime
- Apply scalafmt formatting across the whole existing codebase (discovered during persistence_20260922: scalafmt was never actually run before — sbt-scalafmt/scoverage plugins didn't even exist)
- Reservation expiry/release (unpaid orders currently lock stock forever — inventory-service has no cancel/release mechanism)
- Payment integration (payment-service + payment status surfaced on GET /orders/{id}, alongside the order `status` field)

---
