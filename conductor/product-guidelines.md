# Product Guidelines

## Error Handling
- Domain errors are modeled as sealed traits/ADTs (typed errors), never as exceptions used for control flow.
- Exceptions are reserved for truly unrecoverable failures (bugs, environment failures).
- HTTP-facing services map typed domain errors explicitly to HTTP status codes at the edge — no implicit/central exception-to-status translation for domain errors.

## purerest API Style
- Public APIs are tagless-final / typeclass-based (`F[_]: Async`, etc.), following idiomatic Typelevel/Cats Effect conventions.
- Consumers wire purerest in via explicit combinators/middleware, not annotations, reflection, or macros.
- Effects are always represented in types — no hidden side effects.

## Code Style
- Formatted with scalafmt, default Scala 3 style.
- No `null`, no mutable `var` in domain code — `Option`/`Either`/immutable data structures throughout.
- No exceptions for control flow — errors are always represented in the return type (`Either`, `EitherT`, or equivalent).

## API Conventions
- Plain resource JSON responses with standard HTTP status codes — no envelope spec (e.g. no JSON:API).
- Errors returned as a small JSON error object whose HTTP status matches the mapped domain error.

## Naming
- Services are named `<domain>-service` (e.g. `order-service`, `inventory-service`).
- The platform library is `purerest`.
