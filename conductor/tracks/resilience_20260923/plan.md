# Plan: Resilience (Retry + Circuit Breaker) for purerest's HttpClient

## Phase 1: Retry Combinator (cats-retry) [checkpoint: cad8a5c]
- [x] Task: Add `cats-retry` dependency to `purerest`. [e0542b6]
- [x] Task: Write failing tests against a stub `Client[F]`: a 5xx/connection-error/timeout response is retried up to max attempts then gives up; a 4xx response is never retried (Red). [72bee71]
- [x] Task: Implement `Retry.middleware[F[_]: Async](config: RetryConfig)(client: Client[F]): Client[F]` in `purerest.resilience`, using cats-retry's exponential-backoff-with-jitter policy (Green). Signature relaxed to `Temporal[F]` (sufficient, more general than `Async`). No jitter — see tech-stack.md's dated note: delegates execution to http4s's own `Retry` middleware (Resource-safe), cats-retry supplies backoff only; `fullJitter` incompatible with http4s's pure backoff signature. [52fcd28] [8d2a40d]
- [x] Task: Conductor - User Manual Verification 'Phase 1: Retry Combinator (cats-retry)' (Protocol in workflow.md) — no wiring into a running service yet (that's Phase 3), so verification is the automated test suite itself: full purerest/test green (18/18), including RetrySuite's 6 scenarios against a stub Client[F]. [cad8a5c]

## Phase 2: Circuit Breaker (resilience4j-wrapped) [checkpoint: 7dc7dc9]
- [x] Task: Add `resilience4j-circuitbreaker` dependency to `purerest`. [0cd1548]
- [x] Task: Write failing tests against a stub `Client[F]`: breaker stays closed under successes; opens after the configured failure threshold; open-state calls fail fast with a typed error and never reach the stub client; transitions to half-open after the reset timeout and closes again on a successful trial call (Red). [ff6b743]
- [x] Task: Implement `CircuitBreaker.middleware[F[_]: Async](config: CircuitBreakerConfig)(client: Client[F]): Client[F]` in `purerest.resilience`, driving resilience4j-circuitbreaker's core `CircuitBreaker`; define a typed `CircuitBreakerOpen` error (not a leaked resilience4j exception) for rejected calls (Green). Uses `tryAcquirePermission()`/`onSuccess`/`onError` directly via `Async[F].delay` (not `guaranteeCase`, since the acquire/release-style hook it provides didn't fit the acquire→run→report shape as cleanly as explicit sequencing). [121c916] [e76e860]
- [x] Task: Conductor - User Manual Verification 'Phase 2: Circuit Breaker (resilience4j-wrapped)' (Protocol in workflow.md) — no wiring into a running service yet (Phase 3); verification is the automated test suite: full purerest/test green (21/21), including CircuitBreakerSuite's 3 scenarios (closed/open/half-open-to-closed). [7dc7dc9]

## Phase 3: Compose + Wire into order-service
- [x] Task: Write a failing test for the composed `Resilience.middleware` (retry wrapping circuit breaker) against a stub client, confirming retries pass through the breaker and a breaker-open rejection is not endlessly retried (Red). [731b4ec]
- [x] Task: Implement `Resilience.middleware[F[_]: Async](config: ResilienceConfig)(client: Client[F]): Client[F]` composing `Retry.middleware(Retry.middleware's config)(CircuitBreaker.middleware(...)(client))`; log retries and breaker-open rejections via purerest's structured logging (Green). Making this test pass caught a real bug in CircuitBreaker.middleware (5xx responses were being recorded as breaker successes — see tech-stack.md); fixed via resilience4j's `recordResult` predicate. [93e64de] [28dcc06]
- [ ] Task: Wire `Resilience.middleware(...)(ClientTracing.middleware(tracer)(httpClient))` into `order-service`'s `Main.scala` for its `InventoryClient`.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Compose + Wire into order-service' (Protocol in workflow.md)

## Phase 4: Induced Failure in inventory-service
- [ ] Task: Write a failing test: with `INVENTORY_INDUCED_FAILURE_RATE=1.0`, `inventory-service`'s reserve endpoint returns 500; with `INVENTORY_INDUCED_DELAY_MS` set, the response is measurably delayed (Red).
- [ ] Task: Add `INVENTORY_INDUCED_FAILURE_RATE`/`INVENTORY_INDUCED_DELAY_MS` env-var-driven injection to `inventory-service`'s `InventoryRoutes`/`Main.scala`, defaulting to off/0 (Green).
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Induced Failure in inventory-service' (Protocol in workflow.md) — end-to-end: induced transient failures get retried and `POST /orders` still succeeds; sustained failure trips the circuit breaker and subsequent calls fail fast.

## Phase 5: Coverage & Cleanup
- [ ] Task: Run `sbt coverage purerest/test inventoryService/test orderService/test coverageReport`; confirm 100% statement/branch coverage on every file this track adds or changes.
- [ ] Task: Run `scalafmtOnly` scoped to this track's own files (matching prior tracks' precedent of not reformatting the wider pre-existing codebase).
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Coverage & Cleanup' (Protocol in workflow.md)
