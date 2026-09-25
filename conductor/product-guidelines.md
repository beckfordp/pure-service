# Product Guidelines

## Functional Programming Principles (project-wide, not just purerest)
- Domain errors are modeled as sealed traits/ADTs (typed errors), never as exceptions used for
  control flow. Exceptions are reserved for truly unrecoverable failures (bugs, environment
  failures) — never for an expected, nameable failure case.
- No exceptions for control flow, anywhere — errors are always represented in the return type
  (`Either`, `EitherT`, or an equivalent typed result), all the way from the point of failure to
  the HTTP edge.
- This scopes to *our own* domain/application errors — an expected, nameable failure case we
  can enumerate (not found, invalid input, a downstream dependency unavailable, and so on). It
  does not require eliminating exceptions that are part of a third-party framework's own API
  contract (e.g. http4s's `Client[F]`/`HttpRoutes[F]` are inherently `MonadError`/
  `raiseError`-based; wrapping every third-party call in `Either` would mean reinventing those
  abstractions) or genuine system/environment failures (a config load failure at startup, a
  JVM-level error). The obligation is to catch our own expected failure modes at the point
  they're known and convert them to a typed result before they reach a caller or an HTTP
  response — not to eliminate every `Throwable` from the codebase.
- No partial functions on the happy path — no `Option.get`/`Either.right.get`, no `head` on a
  possibly-empty collection, no non-exhaustive pattern match relying on a runtime `MatchError`.
  Prefer pattern matching, `fold`, or explicit handling of every case.
- No `null`, no mutable `var` in domain code — `Option`/`Either`/immutable data structures
  throughout.
- Any inherently impure operation (randomness, wall-clock time, mutable state) is captured in
  the effect type (`F[_].delay`, or a dedicated capability like `Random[F]`/`Clock[F]`) at the
  point it happens — never executed as a bare side effect and only wrapped incidentally.
- HTTP-facing services map typed domain errors explicitly to HTTP status codes at the edge — no
  implicit/central exception-to-status translation for domain errors.

## purerest API Style
- Public APIs are tagless-final / typeclass-based (`F[_]: Async`, etc.), following idiomatic Typelevel/Cats Effect conventions.
- Consumers wire purerest in via explicit combinators/middleware, not annotations, reflection, or macros.

## Code Style
- Formatted with scalafmt, default Scala 3 style.

## API Conventions
- Plain resource JSON responses with standard HTTP status codes — no envelope spec (e.g. no JSON:API).
- Errors returned as a small JSON error object whose HTTP status matches the mapped domain error.

## Naming
- Services are named `<domain>-service` (e.g. `order-service`, `inventory-service`).
- The platform library is `purerest`.
