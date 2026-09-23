package orderservice

import cats.effect.IO
import cats.syntax.all._
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import munit.CatsEffectSuite
import org.testcontainers.utility.DockerImageName

class OrderStorePostgresSuite extends CatsEffectSuite with TestContainerForAll {

  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(dockerImageName =
      DockerImageName.parse("postgres:16-alpine")
    )

  private def configFor(postgres: PostgreSQLContainer): PostgresConfig =
    PostgresConfig(
      host = postgres.host,
      port = postgres.mappedPort(5432),
      database = postgres.databaseName,
      user = postgres.username,
      password = postgres.password
    )

  test("create persists an order with its reservation and returns it") {
    withContainers { postgres =>
      val config = configFor(postgres)
      val reservationId = java.util.UUID.randomUUID().toString
      Migrations.run[IO](config) *> OrderStore.postgres[IO](config).use { store =>
        store.create("widget", 2, reservationId, 2).map { order =>
          assertEquals(order.item, "widget")
          assertEquals(order.quantity, 2)
          assertEquals(order.status, "reserved")
          assertEquals(order.reservationId, reservationId)
          assertEquals(order.reservedQuantity, 2)
          assert(order.id.nonEmpty)
        }
      }
    }
  }

  test("create produces distinct ids across calls") {
    withContainers { postgres =>
      val config = configFor(postgres)
      Migrations.run[IO](config) *> OrderStore.postgres[IO](config).use {
        store =>
          for {
            first <- store.create("widget", 1, java.util.UUID.randomUUID().toString, 1)
            second <- store.create("widget", 1, java.util.UUID.randomUUID().toString, 1)
          } yield assertNotEquals(first.id, second.id)
      }
    }
  }

  test("get returns the persisted order, including its reservation") {
    withContainers { postgres =>
      val config = configFor(postgres)
      val reservationId = java.util.UUID.randomUUID().toString
      Migrations.run[IO](config) *> OrderStore.postgres[IO](config).use { store =>
        for {
          created <- store.create("widget", 3, reservationId, 3)
          found <- store.get(created.id)
        } yield assertEquals(found, Some(created))
      }
    }
  }

  test("get returns None for an unknown id") {
    withContainers { postgres =>
      val config = configFor(postgres)
      Migrations.run[IO](config) *> OrderStore.postgres[IO](config).use { store =>
        store.get(java.util.UUID.randomUUID().toString).map(assertEquals(_, None))
      }
    }
  }
}
