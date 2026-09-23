package orderservice

import cats.effect.{Async, Ref, Resource, Sync}
import cats.effect.std.Console
import cats.syntax.all._
import fs2.io.net.Network
import skunk.Session
import skunk.codec.all._
import skunk.implicits._

import java.util.UUID

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

  private val insertOrder: skunk.Command[(UUID, String, Int)] =
    sql"insert into orders (id, item, quantity) values ($uuid, $varchar, $int4)".command

  def postgres[F[_]: Async: Console: Network](
      config: PostgresConfig
  ): Resource[F, OrderStore[F]] = {
    import org.typelevel.otel4s.trace.Tracer.Implicits.noop
    import org.typelevel.otel4s.metrics.Meter.Implicits.noop
    Session
      .Builder[F]
      .withHost(config.host)
      .withPort(config.port)
      .withUserAndPassword(config.user, config.password)
      .withDatabase(config.database)
      .pooled(max = 10)
      .map { pool =>
        new OrderStore[F] {
          def create(item: String, quantity: Int): F[Order] =
            pool.use { session =>
              for {
                id <- Sync[F].delay(UUID.randomUUID())
                _ <- session
                  .prepare(insertOrder)
                  .flatMap(_.execute((id, item, quantity)))
              } yield Order(id.toString, item, quantity)
            }
        }
      }
  }
}
