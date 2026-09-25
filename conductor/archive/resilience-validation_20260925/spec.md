# Spec: Drive a Gatling scenario that ramps inventory-service's induced-failure rate, and add Grafana panels validating resilience effectiveness

## Overview
Extends the Gatling load-test module with a second, concurrent scenario that drives
inventory-service's induced-failure rate live through a sequence of values (0.0 -> 0.6 -> 0.0)
via the `PATCH /admin/induced-failure` endpoint added in the previous track — replacing the
current two-pass-with-container-restart approach in `verify-observability-stack.sh` with a
single continuous run that demonstrates a full circuit-breaker trip-and-recovery cycle. Adds
two new Grafana panels — a circuit-breaker state timeline (visual trip/recovery) and a retry
success-rate trend — built entirely from metrics that already exist, so the dashboard can show
whether purerest's resilience config is *effective*, not just active. Directly answers
Iteration 2's open question (`product.md`).

## Functional Requirements
1. `OrderPlacementSimulation` gains a second Gatling scenario that, running concurrently with
   the existing order-placement load, issues `PATCH` requests to inventory-service's
   `/admin/induced-failure` on a timed schedule: `failureRate=0.0` (baseline) ->
   `failureRate=0.6` (known to reliably trip the breaker) -> back to `0.0` (recovery), with
   each phase comfortably longer than order-service's 30s `resetTimeout`.
2. inventory-service's base URL for the PATCH schedule is configurable the same way
   `OrderPlacementSimulation`'s `baseUrl` already is (`-D` system property, `localhost:8081`
   default).
3. `scripts/verify-observability-stack.sh` is recovered from `scripts/archive/` back to
   `scripts/` (the genuine ongoing use that triggers recovery, per the earlier scripts-cleanup
   decision), and its two-pass structure (healthy pass + container-restarted degraded pass) is
   replaced by a single run of the new ramping scenario. Existing Prometheus/dashboard/Kibana
   assertions are preserved; retry/circuit-breaker assertions are adapted to the single-run
   ramp.
4. `README.md`/`tech-stack.md` references to the script's archived path are updated back to the
   recovered path.
5. Two new panels added to `observability/grafana/provisioning/dashboards/purerest.json`:
   "Circuit Breaker: State Timeline" (`state-timeline` panel plotting
   `purerest_circuit_breaker_state`) and "Retry Success Rate" (`timeseries` panel showing
   successful-including-retried vs. exhausted fraction, from `purerest_retry_attempts_total`).
6. `verify-observability-stack.sh` asserts (via Grafana's HTTP API) that both new panels'
   queries resolve non-empty data after the ramping run.

## Non-Functional Requirements
- No new metrics/instrumentation in purerest — both panels are built entirely from
  already-emitted series.
- Exact rate-schedule timings are calibrated empirically during implementation (same approach
  used for the prior track's 0.3->0.5 calibration); this spec fixes the sequence and shape, not
  exact durations.

## Acceptance Criteria
- `sbt loadTest/Gatling/test` runs both scenarios concurrently and completes successfully.
- Prometheus shows `purerest_circuit_breaker_state` transitioning CLOSED -> OPEN ->
  (HALF_OPEN) -> CLOSED within the single run.
- Recovered `scripts/verify-observability-stack.sh` runs end-to-end with the new single
  ramping run, asserting both new panels resolve real, non-empty data.
- The Grafana dashboard visibly shows a full trip-and-recovery cycle and a retry success-rate
  trend, viewable in a browser.
- Full existing `sbt test` suite remains green (no application-code changes expected).

## Out of Scope
- Any change to purerest's metrics/instrumentation itself.
- `scripts/loadtest-purerest.sh` and other still-archived scripts — untouched.
- A numeric/computed "seconds since last trip" panel — the state-timeline is the chosen
  approach.
- Any change to the retry/circuit-breaker config itself — this track validates the existing
  config, doesn't tune it.
