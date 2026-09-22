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

object InventoryRoutes {

  private val reserveEndpoint: PublicEndpoint[ReserveRequest, Unit, Reservation, Any] =
    endpoint.post
      .in("inventory" / "reserve")
      .in(jsonBody[ReserveRequest])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[Reservation])

  def serverEndpoint[F[_]: Async](store: InventoryStore[F], logger: StructuredLogger[F]): ServerEndpoint[Any, F] =
    reserveEndpoint.serverLogicSuccess[F] { req =>
      for {
        reservation <- store.reserve(req.item, req.quantity)
        _ <- logger.info(s"reserved ${reservation.quantity} x ${reservation.item} (reservation ${reservation.id})")
      } yield reservation
    }

  def routes[F[_]: Async](store: InventoryStore[F], logger: StructuredLogger[F]): HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(List(serverEndpoint(store, logger)))
}
