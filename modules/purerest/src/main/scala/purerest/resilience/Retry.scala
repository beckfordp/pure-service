package purerest.resilience

import cats.effect.Temporal
import cats.effect.kernel.Resource
import cats.syntax.all._
import org.http4s.Response
import org.http4s.client.Client
import org.http4s.client.middleware.{Retry => Http4sRetry, RetryPolicy => Http4sRetryPolicy}
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.noop.NoOpFactory
import retry.{PolicyDecision, RetryPolicies, RetryStatus}

import scala.concurrent.duration.{Duration, FiniteDuration}

/** Config for [[Retry.middleware]]. `maxRetries` is retries, not attempts (so
  * `maxRetries = 2` means up to 3 total attempts). `baseDelay` is doubled on each
  * successive retry (exponential backoff, via cats-retry's `RetryPolicies`).
  */
final case class RetryConfig(maxRetries: Int, baseDelay: FiniteDuration)

/** Retries requests that fail transiently: 5xx responses, connection errors, and
  * timeouts. Never retries 4xx responses (a bad request can't succeed by repeating
  * it) or other exception types.
  *
  * Delegates the actual retry loop to http4s's own `Retry` client middleware, which
  * already solves the tricky problem of safely retrying a `Resource`-based
  * `Client[F]` (releasing each failed attempt's connection/body before trying
  * again). cats-retry's `RetryPolicies` supplies the backoff schedule (composed
  * under `cats.Id` since http4s's own backoff slot is a pure function, incompatible
  * with cats-retry's `Random`-effectful jitter policies — so this uses
  * `limitRetries` + `exponentialBackoff`, not `fullJitter`).
  */
object Retry {

  def isRetriableError(error: Throwable): Boolean = error match {
    case _: java.net.ConnectException             => true
    case _: java.util.concurrent.TimeoutException => true
    case _                                         => false
  }

  def isRetriableResponse[F[_]](response: Response[F]): Boolean =
    response.status.responseClass == org.http4s.Status.ServerError

  private def backoff(config: RetryConfig): Int => Option[FiniteDuration] = {
    val policy = RetryPolicies
      .limitRetries[cats.Id](config.maxRetries)
      .join(RetryPolicies.exponentialBackoff[cats.Id](config.baseDelay))
    attempts =>
      policy.decideNextRetry((), RetryStatus(retriesSoFar = attempts - 1, Duration.Zero, None)) match {
        case PolicyDecision.DelayAndRetry(delay) => Some(delay)
        case PolicyDecision.GiveUp                => None
      }
  }

  def middleware[F[_]: Temporal](config: RetryConfig)(logger: StructuredLogger[F])(client: Client[F]): Client[F] = {
    implicit val loggerFactory: org.typelevel.log4cats.LoggerFactory[F] = NoOpFactory[F]
    val policy = Http4sRetryPolicy[F](
      backoff(config),
      retriable = (_, result) =>
        result match {
          case Right(response) => isRetriableResponse(response)
          case Left(error)     => isRetriableError(error)
        }
    )
    val retried = Http4sRetry.create(policy, logRetries = false)(client)
    Client[F] { req =>
      retried
        .run(req)
        .evalMap { response =>
          response.attributes.lookup(Http4sRetry.AttemptCountKey) match {
            case Some(attempts) if attempts > 1 =>
              logger.info(s"Request to ${req.uri} succeeded after $attempts attempt(s)").as(response)
            case _ => response.pure[F]
          }
        }
        .onError { case error => Resource.eval(logger.warn(error)(s"Request to ${req.uri} failed after retries")) }
    }
  }
}
