package purerest.resilience

import cats.effect.Async
import cats.effect.kernel.Resource
import cats.syntax.all._
import io.github.resilience4j.circuitbreaker.{
  CircuitBreakerConfig => R4jCircuitBreakerConfig
}
import io.github.resilience4j.circuitbreaker.{
  CircuitBreaker => R4jCircuitBreaker
}
import org.http4s.Response
import org.http4s.client.Client
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter

import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NoStackTrace

/** Config for [[CircuitBreaker.middleware]]. `failureThreshold` failures in a
  * row trip the breaker open (a sliding window of exactly `failureThreshold`
  * calls, all of which must fail — i.e. a 100% failure rate — since
  * resilience4j's engine is rate-based, not raw-count-based). `resetTimeout` is
  * how long the breaker stays open before allowing a single half-open trial
  * call.
  */
final case class CircuitBreakerConfig(
    failureThreshold: Int,
    resetTimeout: FiniteDuration
)

/** Raised when a call is rejected because the circuit breaker is open. Never
  * resilience4j's own `CallNotPermittedException` — that type never leaves this
  * object, per purerest's "no third-party types in the public API" stance.
  */
case object CircuitBreakerOpen
    extends RuntimeException("Circuit breaker is open")
    with NoStackTrace

/** Wraps resilience4j-circuitbreaker's core, non-reactive `CircuitBreaker`
  * engine as a pure `Client[F] => Client[F]` combinator. resilience4j's own
  * state-machine methods (`tryAcquirePermission`/`onSuccess`/`onError`) are
  * synchronous, in-memory, non-blocking operations — safe to call directly from
  * Cats Effect via `Sync`/ `Temporal`, no thread-shifting required.
  */
object CircuitBreaker {

  /** resilience4j's `onResult` hook uses this predicate to decide whether a
    * returned value (not a thrown exception) should count as a failure for the
    * breaker's sliding window. Without this, a 5xx `Response` — returned as a
    * normal value by `Client[F].run`, never thrown — would be indistinguishable
    * from a real success.
    *
    * Not private: the `case _ => false` fallback (required for type-safety
    * against resilience4j's `Predicate[Any]` signature, since `onResult` is
    * never actually called with anything but a `Response[F]` in this codebase)
    * is otherwise unreachable through `middleware`'s public API — tested
    * directly instead.
    */
  private[resilience] val isFailureResult: java.util.function.Predicate[Any] =
    (result: Any) =>
      result match {
        case response: Response[?] =>
          response.status.responseClass == org.http4s.Status.ServerError
        case _ => false
      }

  /** Records a `purerest.circuit_breaker.state_transitions` measurement when
    * the breaker's state differs before and after a call — net of any
    * intermediate hop (e.g. an OPEN -> HALF_OPEN -> CLOSED recovery is recorded
    * as a single OPEN -> CLOSED transition), since this middleware only
    * observes state synchronously before and after its own acquire/run/report
    * sequence, not via a separate event listener.
    */
  private def recordTransition[F[_]: Async](
      meter: Meter[F]
  )(before: R4jCircuitBreaker.State, after: R4jCircuitBreaker.State): F[Unit] =
    if (before == after) Async[F].unit
    else
      meter
        .counter[Long]("purerest.circuit_breaker.state_transitions")
        .create
        .flatMap(
          _.inc(
            Attribute("from_state", before.name),
            Attribute("to_state", after.name)
          )
        )

  private def recordRejection[F[_]: Async](meter: Meter[F]): F[Unit] =
    meter
      .counter[Long]("purerest.circuit_breaker.calls_rejected")
      .create
      .flatMap(_.inc())

  def middleware[F[_]: Async](
      config: CircuitBreakerConfig
  )(meter: Meter[F])(client: Client[F]): Client[F] = {
    val r4jConfig = R4jCircuitBreakerConfig
      .custom()
      .slidingWindowType(R4jCircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
      .slidingWindowSize(config.failureThreshold)
      .minimumNumberOfCalls(config.failureThreshold)
      .failureRateThreshold(100.0f)
      .waitDurationInOpenState(JDuration.ofMillis(config.resetTimeout.toMillis))
      .permittedNumberOfCallsInHalfOpenState(1)
      .recordResult(isFailureResult)
      .build()
    val breaker = R4jCircuitBreaker.of("purerest", r4jConfig)

    Client[F] { req =>
      Resource.eval(Async[F].delay(breaker.getState)).flatMap { stateBefore =>
        Resource.eval(Async[F].delay(breaker.tryAcquirePermission())).flatMap {
          case false =>
            Resource.eval(
              recordRejection(meter) *> Async[F]
                .raiseError[Response[F]](CircuitBreakerOpen)
            )
          case true =>
            Resource.eval(Async[F].monotonic).flatMap { start =>
              client.run(req).attempt.evalMap {
                case Right(response) =>
                  Async[F].monotonic
                    .flatMap(end =>
                      Async[F].delay(
                        breaker.onResult(
                          (end - start).toNanos,
                          TimeUnit.NANOSECONDS,
                          response
                        )
                      ) *> Async[F].delay(breaker.getState)
                    )
                    .flatMap(stateAfter =>
                      recordTransition(meter)(stateBefore, stateAfter)
                    )
                    .as(response)
                case Left(error) =>
                  Async[F].monotonic
                    .flatMap(end =>
                      Async[F].delay(
                        breaker.onError(
                          (end - start).toNanos,
                          TimeUnit.NANOSECONDS,
                          error
                        )
                      ) *> Async[F].delay(breaker.getState)
                    )
                    .flatMap(stateAfter =>
                      recordTransition(meter)(stateBefore, stateAfter)
                    ) *>
                    Async[F].raiseError[Response[F]](error)
              }
            }
        }
      }
    }
  }
}
