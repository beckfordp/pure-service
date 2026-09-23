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

/** Deliberately induced failure/latency, so purerest's resilience combinators
  * can be exercised end-to-end against a real flaky service instead of only
  * against stub clients in unit tests. `failureRate` is a 0.0-1.0 probability
  * of returning a 500 for a given request; `delay` is added latency before
  * responding. Both default to off/zero so normal runs are unaffected.
  */
final case class InducedFailureConfig(
    failureRate: Double,
    delay: FiniteDuration
)

object InducedFailureConfig {
  val disabled: InducedFailureConfig =
    InducedFailureConfig(failureRate = 0.0, delay = Duration.Zero)
}

object InventoryRoutes {

  private val reserveEndpoint
      : PublicEndpoint[ReserveRequest, Unit, Reservation, Any] =
    endpoint.post
      .in("inventory" / "reserve")
      .in(jsonBody[ReserveRequest])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[Reservation])

  private def maybeInduceFailure[F[_]: Async](
      config: InducedFailureConfig,
      logger: StructuredLogger[F],
      item: String,
      quantity: Int
  ): F[Unit] =
    Async[F].sleep(config.delay) *>
      (if (config.failureRate <= 0.0) Async[F].unit
       else
         Async[F]
           .delay(scala.util.Random.nextDouble() < config.failureRate)
           .flatMap {
             case true =>
               logger.warn(
                 Map("item" -> item, "quantity" -> quantity.toString)
               )(
                 "Induced failure triggered"
               ) *> Async[F].raiseError(new RuntimeException("Induced failure"))
             case false => Async[F].unit
           })

  def serverEndpoint[F[_]: Async](
      store: InventoryStore[F],
      logger: StructuredLogger[F],
      induced: InducedFailureConfig = InducedFailureConfig.disabled
  ): ServerEndpoint[Any, F] =
    reserveEndpoint.serverLogicSuccess[F] { req =>
      for {
        _ <- logger.info(
          Map(
            "method" -> "POST",
            "path" -> "/inventory/reserve",
            "item" -> req.item,
            "quantity" -> req.quantity.toString
          )
        )("Received request")
        _ <- maybeInduceFailure[F](induced, logger, req.item, req.quantity)
        // No error-path logging here: InventoryStore.reserve is an unconditional
        // in-memory write that can't fail (see InventoryStore.inMemory) — nothing
        // to catch. order-service's InventoryClient.reserve is the real,
        // catchable failure mode for this call path (see OrderRoutes.scala).
        reservation <- store.reserve(req.item, req.quantity)
        _ <- logger.info(
          Map(
            "reservation_id" -> reservation.id,
            "item" -> reservation.item,
            "quantity" -> reservation.quantity.toString
          )
        )("Request completed")
      } yield reservation
    }

  def routes[F[_]: Async](
      store: InventoryStore[F],
      logger: StructuredLogger[F],
      induced: InducedFailureConfig = InducedFailureConfig.disabled
  ): HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(
      List(serverEndpoint(store, logger, induced))
    )
}
