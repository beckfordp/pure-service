package inventoryservice

import cats.effect.Async
import cats.syntax.all._
import org.http4s.HttpRoutes
import org.typelevel.log4cats.StructuredLogger
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.{Duration, FiniteDuration}

/** Deliberately induced failure/latency, so purerest's resilience combinators can be
  * exercised end-to-end against a real flaky service instead of only against stub
  * clients in unit tests. `failureRate` is a 0.0-1.0 probability of returning a 500
  * for a given request; `delay` is added latency before responding. Both default to
  * off/zero so normal runs are unaffected.
  */
final case class InducedFailureConfig(failureRate: Double, delay: FiniteDuration)

object InducedFailureConfig {
  val disabled: InducedFailureConfig = InducedFailureConfig(failureRate = 0.0, delay = Duration.Zero)
}

object InventoryRoutes {

  private val reserveEndpoint: PublicEndpoint[ReserveRequest, Unit, Reservation, Any] =
    endpoint.post
      .in("inventory" / "reserve")
      .in(jsonBody[ReserveRequest])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[Reservation])

  private def maybeInduceFailure[F[_]: Async](config: InducedFailureConfig): F[Unit] =
    Async[F].sleep(config.delay) *>
      (if (config.failureRate <= 0.0) Async[F].unit
       else
         Async[F].delay(scala.util.Random.nextDouble() < config.failureRate).flatMap {
           case true  => Async[F].raiseError(new RuntimeException("Induced failure"))
           case false => Async[F].unit
         })

  def serverEndpoint[F[_]: Async](
      store: InventoryStore[F],
      logger: StructuredLogger[F],
      induced: InducedFailureConfig = InducedFailureConfig.disabled
  ): ServerEndpoint[Any, F] =
    reserveEndpoint.serverLogicSuccess[F] { req =>
      for {
        _ <- maybeInduceFailure[F](induced)
        reservation <- store.reserve(req.item, req.quantity)
        _ <- logger.info(s"reserved ${reservation.quantity} x ${reservation.item} (reservation ${reservation.id})")
      } yield reservation
    }

  def routes[F[_]: Async](
      store: InventoryStore[F],
      logger: StructuredLogger[F],
      induced: InducedFailureConfig = InducedFailureConfig.disabled
  ): HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(List(serverEndpoint(store, logger, induced)))
}
