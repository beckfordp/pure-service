package orderservice

import cats.effect.Async
import cats.syntax.all._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.HttpRoutes
import org.typelevel.log4cats.StructuredLogger
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

final case class CreateOrderRequest(item: String, quantity: Int)

object CreateOrderRequest {
  implicit val codec: Codec[CreateOrderRequest] = deriveCodec
}

final case class OrderResponse(id: String, item: String, quantity: Int, reservationId: String)

object OrderResponse {
  implicit val codec: Codec[OrderResponse] = deriveCodec
}

object OrderRoutes {

  private val createOrderEndpoint: PublicEndpoint[CreateOrderRequest, Unit, OrderResponse, Any] =
    endpoint.post
      .in("orders")
      .in(jsonBody[CreateOrderRequest])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[OrderResponse])

  def serverEndpoint[F[_]: Async](
      store: OrderStore[F],
      inventory: InventoryClient[F],
      logger: StructuredLogger[F]
  ): ServerEndpoint[Any, F] =
    createOrderEndpoint.serverLogicSuccess[F] { req =>
      for {
        reservation <- inventory.reserve(req.item, req.quantity)
        order <- store.create(req.item, req.quantity)
        _ <- logger.info(s"created order ${order.id} for ${order.quantity} x ${order.item} (reservation ${reservation.id})")
      } yield OrderResponse(order.id, order.item, order.quantity, reservation.id)
    }

  def routes[F[_]: Async](
      store: OrderStore[F],
      inventory: InventoryClient[F],
      logger: StructuredLogger[F]
  ): HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(List(serverEndpoint(store, inventory, logger)))
}
