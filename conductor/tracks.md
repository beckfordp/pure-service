# Project Tracks

This file tracks all major tracks for the project.

---

- [x] **Track: Publish purerest as a versioned jar**
  *Link: [./tracks/publish-jar_20260923/](./tracks/publish-jar_20260923/)*

---

## Backlog

Title-only placeholders for future tracks — not yet detailed (no spec/plan, no linked
folder), so `/conductor:implement` cannot pick these up by accident. Reorder freely as
priorities change. When ready to work on one, run `/conductor:newTrack <title>` to go
through the spec/plan questions and promote it into a real track below.

- Smoke-test purerest consumption as an external published jar (real `libraryDependencies` resolution, not the internal `ProjectRef`) to prove it works as a real binary dependency for another microservice
- Add a Gatling-based load-test module that exercises purerest repeatedly under sustained traffic to generate realistic RED + resilience metrics
- Stand up a local production-like observability stack (Prometheus + Grafana for metrics dashboards, ELK + Kibana for trace-correlated logs) wired to order-service/inventory-service's real output

---
