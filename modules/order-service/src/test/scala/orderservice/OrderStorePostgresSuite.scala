package orderservice

import cats.effect.IO
import cats.syntax.all._
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import munit.CatsEffectSuite
import org.testcontainers.utility.DockerImageName

class OrderStorePostgresSuite extends CatsEffectSuite with TestContainerForAll {

  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(dockerImageName = DockerImageName.parse("postgres:16-alpine"))

  private def configFor(postgres: PostgreSQLContainer): PostgresConfig =
    PostgresConfig(
      host = postgres.host,
      port = postgres.mappedPort(5432),
      database = postgres.databaseName,
      user = postgres.username,
      password = postgres.password
    )

  test("create persists an order and returns it") {
    withContainers { postgres =>
      val config = configFor(postgres)
      Migrations.run[IO](config) *> OrderStore.postgres[IO](config).use { store =>
        store.create("widget", 2).map { order =>
          assertEquals(order.item, "widget")
          assertEquals(order.quantity, 2)
          assert(order.id.nonEmpty)
        }
      }
    }
  }

  test("create produces distinct ids across calls") {
    withContainers { postgres =>
      val config = configFor(postgres)
      Migrations.run[IO](config) *> OrderStore.postgres[IO](config).use { store =>
        for {
          first <- store.create("widget", 1)
          second <- store.create("widget", 1)
        } yield assertNotEquals(first.id, second.id)
      }
    }
  }
}
