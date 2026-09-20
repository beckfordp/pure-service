package orderservice

import cats.effect.Concurrent
import cats.syntax.all._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl

final case class CreateOrderRequest(item: String, quantity: Int)

object CreateOrderRequest {
  implicit val codec: Codec[CreateOrderRequest] = deriveCodec
}

final case class OrderResponse(id: String, item: String, quantity: Int, reservationId: String)

object OrderResponse {
  implicit val codec: Codec[OrderResponse] = deriveCodec
}

object OrderRoutes {
  def routes[F[_]: Concurrent](store: OrderStore[F], inventory: InventoryClient[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] { case req @ POST -> Root / "orders" =>
      for {
        body <- req.as[CreateOrderRequest]
        reservation <- inventory.reserve(body.item, body.quantity)
        order <- store.create(body.item, body.quantity)
        resp <- Created(OrderResponse(order.id, order.item, order.quantity, reservation.id))
      } yield resp
    }
  }
}
