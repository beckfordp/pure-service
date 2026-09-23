# Project Tracks

This file tracks all major tracks for the project.

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
- purerest metrics (RED metrics — request rate/errors/duration — for inbound and outbound calls, per product.md's Core Use Case; build on otel4s's `Meter` API rather than a separate library like Micrometer/Prometheus-client-java, since tracing already uses otel4s — export via OTel's Prometheus exporter. Include retry-attempt/circuit-breaker-state metrics, now that the resilience track is built.)

---
