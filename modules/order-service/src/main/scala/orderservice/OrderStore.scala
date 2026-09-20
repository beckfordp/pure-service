package orderservice

import cats.effect.{Ref, Sync}
import cats.syntax.all._

final case class Order(id: String, item: String, quantity: Int)

trait OrderStore[F[_]] {
  def create(item: String, quantity: Int): F[Order]
}

object OrderStore {
  def inMemory[F[_]: Sync]: F[OrderStore[F]] =
    Ref.of[F, Map[String, Order]](Map.empty).map { ref =>
      new OrderStore[F] {
        def create(item: String, quantity: Int): F[Order] =
          for {
            id <- Sync[F].delay(java.util.UUID.randomUUID().toString)
            order = Order(id, item, quantity)
            _ <- ref.update(_ + (id -> order))
          } yield order
      }
    }
}
