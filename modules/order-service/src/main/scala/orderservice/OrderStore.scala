package orderservice

import cats.effect.{Async, Ref, Resource, Sync}
import cats.effect.std.Console
import cats.syntax.all._
import fs2.io.net.Network
import skunk.Session
import skunk.codec.all._
import skunk.implicits._

import java.time.OffsetDateTime
import java.util.UUID

final case class Order(
    id: String,
    item: String,
    quantity: Int,
    status: String,
    reservationId: String,
    reservedQuantity: Int,
    createdAt: java.time.Instant
)

trait OrderStore[F[_]] {
  def create(
      item: String,
      quantity: Int,
      reservationId: String,
      reservedQuantity: Int
  ): F[Order]
  def get(id: String): F[Option[Order]]
}

object OrderStore {

  private val defaultStatus = "reserved"

  def inMemory[F[_]: Sync]: F[OrderStore[F]] =
    Ref.of[F, Map[String, Order]](Map.empty).map { ref =>
      new OrderStore[F] {
        def create(
            item: String,
            quantity: Int,
            reservationId: String,
            reservedQuantity: Int
        ): F[Order] =
          for {
            id <- Sync[F].delay(java.util.UUID.randomUUID().toString)
            now <- Sync[F].realTimeInstant
            order = Order(
              id,
              item,
              quantity,
              defaultStatus,
              reservationId,
              reservedQuantity,
              now
            )
            _ <- ref.update(_ + (id -> order))
          } yield order

        def get(id: String): F[Option[Order]] = ref.get.map(_.get(id))
      }
    }

  private val insertOrder
      : skunk.Query[(UUID, String, Int, UUID, Int), OffsetDateTime] =
    sql"""
      INSERT INTO orders (id, item, quantity, reservation_id, reserved_quantity)
      VALUES ($uuid, $text, $int4, $uuid, $int4)
      RETURNING created_at
    """.query(timestamptz)

  private val selectOrder
      : skunk.Query[UUID, (String, Int, String, UUID, Int, OffsetDateTime)] =
    sql"""
      SELECT item, quantity, status, reservation_id, reserved_quantity, created_at
      FROM orders
      WHERE id = $uuid
    """.query(text *: int4 *: text *: uuid *: int4 *: timestamptz)

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
          def create(
              item: String,
              quantity: Int,
              reservationId: String,
              reservedQuantity: Int
          ): F[Order] =
            pool.use { session =>
              for {
                id <- Sync[F].delay(UUID.randomUUID())
                reservationUuid <- Sync[F].delay(UUID.fromString(reservationId))
                createdAt <- session
                  .prepare(insertOrder)
                  .flatMap(
                    _.unique(
                      (id, item, quantity, reservationUuid, reservedQuantity)
                    )
                  )
              } yield Order(
                id.toString,
                item,
                quantity,
                defaultStatus,
                reservationId,
                reservedQuantity,
                createdAt.toInstant
              )
            }

          def get(id: String): F[Option[Order]] =
            pool.use { session =>
              for {
                uuid <- Sync[F].delay(UUID.fromString(id))
                row <- session.prepare(selectOrder).flatMap(_.option(uuid))
              } yield row.map {
                case (
                      item,
                      quantity,
                      status,
                      reservationUuid,
                      reservedQuantity,
                      createdAt
                    ) =>
                  Order(
                    id,
                    item,
                    quantity,
                    status,
                    reservationUuid.toString,
                    reservedQuantity,
                    createdAt.toInstant
                  )
              }
            }
        }
      }
  }
}
