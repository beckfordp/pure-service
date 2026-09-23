# Spec: Resilience (Retry + Circuit Breaker) for purerest's HttpClient

## Overview
purerest's HTTP client is currently a bare `EmberClientBuilder.default[F].build` — no retry, no circuit breaker, despite this being named in product.md's Core Use Case as a purerest design driver from day one. This track adds both, composed as a combinator matching the existing `ClientTracing.middleware`/`ServerTracing.middleware` shape, and gives `inventory-service` a way to be deliberately slow/flaky so the behavior can be exercised end-to-end (not just unit-tested against mocks).

## Functional Requirements
1. **Retry** (`purerest.resilience`, using **cats-retry**): exponential backoff with jitter, configurable max attempts. Retries on 5xx responses, connection errors, and timeouts; never retries 4xx (a bad request can't succeed by repeating it).
2. **Circuit breaker** (`purerest.resilience`): wraps **resilience4j-circuitbreaker**'s core, non-reactive `CircuitBreaker` class as an internal engine — driven manually via `F.guaranteeCase` around each call (`acquirePermission`/`onSuccess`/`onError` are synchronous, non-blocking, in-memory state updates, safe to call from Cats Effect this way). resilience4j types never appear in purerest's public API. Standard closed/open/half-open states; a call rejected because the breaker is open surfaces as a typed failure to the caller (per `product-guidelines.md`'s typed-error stance), not a leaked resilience4j exception.
3. **Combinator**: `Resilience.middleware[F[_]: Async](config: ResilienceConfig)(client: Client[F]): Client[F]`, composing retry (outer) around the circuit breaker (inner) — resilience4j's own documented recommended ordering. Applied around the *already-traced* client (`Resilience.middleware(config)(ClientTracing.middleware(tracer)(httpClient))`), so each retry attempt produces its own trace span.
4. **Wiring**: `order-service`'s `Main.scala` constructs the resilient+traced client for its `InventoryClient`, replacing today's bare traced client.
5. **Induced failure in inventory-service**: two new env vars, `INVENTORY_INDUCED_FAILURE_RATE` (0.0–1.0 probability of returning a 500) and `INVENTORY_INDUCED_DELAY_MS` (added latency before responding), both defaulting to off/0 so normal runs are unaffected.

## Non-Functional Requirements
- No behavior change to `POST /orders`'s API contract when `inventory-service` is healthy.
- Config is passed explicitly as constructor/middleware parameters (matching `ClientTracing`/`ServerTracing`'s existing style), not a new config-file mechanism.
- Retries and circuit-breaker rejections are logged (via purerest's existing structured logging) so resilience activity is observable without needing to inspect traces.

## Acceptance Criteria
- With `INVENTORY_INDUCED_FAILURE_RATE` set, transient 5xx failures from inventory-service are retried and `POST /orders` still succeeds (up to the configured max attempts).
- With inventory-service failing continuously, the circuit breaker opens after the configured threshold, and subsequent calls fail fast (no network call) until the reset timeout elapses.
- Each retry attempt appears as its own span in tracing output.
- A circuit-breaker-open rejection surfaces as a typed error, mapped to a clean HTTP response — no leaked resilience4j exception details.
- Unit and integration tests pass; coverage on all new/changed code matches the bar established by prior tracks (100% statement/branch).

## Out of Scope
- Bulkhead/rate-limiting (other resilience4j modules) — only retry + circuit breaker are in scope.
- Server-side resilience (this track is about the outbound client only).
- Metrics on retry attempts/circuit-breaker state transitions — covered by the separate `purerest metrics` backlog item.
- Making retry/circuit-breaker config runtime-adjustable (e.g. via an admin endpoint) — config is fixed at startup.
