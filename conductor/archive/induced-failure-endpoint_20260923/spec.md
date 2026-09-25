# Spec: Add a runtime-adjustable induced-failure endpoint to inventory-service

## Overview
inventory-service's induced-failure config (failureRate, delay) is currently fixed at
container-startup via env vars (`INVENTORY_INDUCED_FAILURE_RATE`/`INVENTORY_INDUCED_DELAY_MS`)
and can only change via a full container restart. This blocks Iteration 2's
resilience-validation goal (`product.md`) — running a Gatling scenario that ramps failure
through a sequence of values mid-run to observe purerest's retry/circuit-breaker behavior. This
track makes the config runtime-mutable via a new admin HTTP endpoint pair, exposed the same
self-documenting way as inventory-service's existing endpoints (tapir, so it appears in Swagger
UI for free), with no new custom UI.

## Functional Requirements
1. inventory-service holds its induced-failure config in a mutable `Ref[F, InducedFailureConfig]`,
   seeded at startup from the existing env vars (unchanged default behavior).
2. `GET /admin/induced-failure` returns the current live config as JSON
   (`{"failureRate": <double>, "delayMs": <long>}`).
3. `PATCH /admin/induced-failure` accepts a JSON body with the same shape and atomically
   replaces the live config; the very next `/inventory/reserve` request observes the new
   values — no restart required.
4. `failureRate` outside `[0.0, 1.0]` or a negative `delayMs` is rejected with `400 Bad Request`
   and a typed JSON error body (per `product-guidelines.md`'s modeled-domain-error convention),
   not silently clamped.
5. Both endpoints are registered as tapir `ServerEndpoint`s alongside the existing
   `/inventory/reserve` endpoint, so they appear in inventory-service's generated OpenAPI
   spec/Swagger UI automatically.
6. Existing behavior is unchanged for callers who never touch the new endpoints: startup still
   reads the same env vars, defaulting to disabled (0.0 / 0ms) exactly as today.

## Non-Functional Requirements
- No authentication on the admin endpoints — matches `product.md`'s Non-Goals and the
  endpoints' local-dev-only, non-customer-facing purpose.
- Reading/updating the Ref must not let `maybeInduceFailure` read a torn/partial config — a
  single atomic `Ref.set`/`.get` per request is sufficient (no CAS semantics needed, exactly
  one writer path).

## Acceptance Criteria
- `GET /admin/induced-failure` on a freshly-started service returns the env-var-seeded (or
  default-disabled) values.
- `PATCH` with a valid body returns success and a subsequent `GET` reflects the new values.
- `PATCH` with `failureRate = 1.5` (or negative `failureRate`/`delayMs`) returns 400 with a
  typed error body, and does not change the live config.
- A `POST /inventory/reserve` issued after `PATCH`ing `failureRate` to `1.0` always fails
  (500); after `PATCH`ing back to `0.0`, requests succeed again — proving the change takes
  effect live, without a restart.
- Both new endpoints appear in inventory-service's Swagger UI alongside `/inventory/reserve`.
- Full test suite green, coverage on changed files per `workflow.md`'s >80% target.

## Out of Scope
- Retry/circuit-breaker config staying runtime-tunable — deferred to a possible future iteration.
- Grafana annotations on rate changes, and the Gatling scenario driving this endpoint through a
  sequence of values — that's the next backlog item.
- Any bespoke control UI beyond the auto-generated Swagger UI.
- Persisting the live-adjusted value across a restart (always resets to the env-var/default on
  startup by design).
