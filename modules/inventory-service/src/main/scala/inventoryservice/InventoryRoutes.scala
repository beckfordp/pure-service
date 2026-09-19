package inventoryservice

import cats.effect.Concurrent
import cats.syntax.all._
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl

object InventoryRoutes {
  def routes[F[_]: Concurrent](store: InventoryStore[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] { case req @ POST -> Root / "inventory" / "reserve" =>
      for {
        body <- req.as[ReserveRequest]
        reservation <- store.reserve(body.item, body.quantity)
        resp <- Created(reservation)
      } yield resp
    }
  }
}
