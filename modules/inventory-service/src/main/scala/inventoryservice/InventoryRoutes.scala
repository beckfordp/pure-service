package inventoryservice

import cats.effect.Concurrent
import cats.syntax.all._
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.typelevel.log4cats.StructuredLogger

object InventoryRoutes {
  def routes[F[_]: Concurrent](store: InventoryStore[F], logger: StructuredLogger[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] { case req @ POST -> Root / "inventory" / "reserve" =>
      for {
        body <- req.as[ReserveRequest]
        reservation <- store.reserve(body.item, body.quantity)
        _ <- logger.info(s"reserved ${reservation.quantity} x ${reservation.item} (reservation ${reservation.id})")
        resp <- Created(reservation)
      } yield resp
    }
  }
}
