package orderservice

import cats.effect.IO
import com.comcast.ip4s._
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import inventoryservice.{InventoryRoutes, InventoryStore}
import munit.CatsEffectSuite
import org.http4s.circe.CirceEntityCodec._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.{Method, Request, Status, Uri}
import org.testcontainers.utility.DockerImageName
import org.typelevel.log4cats.noop.NoOpLogger
import purerest.client.HttpClient

import java.sql.DriverManager

class OrderServicePostgresIntegrationSuite extends CatsEffectSuite with TestContainerForAll {

  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(dockerImageName = DockerImageName.parse("postgres:16-alpine"))

  test("POST /orders reserves stock and persists the order in Postgres") {
    withContainers { postgres =>
      val config = PostgresConfig(
        host = postgres.host,
        port = postgres.mappedPort(5432),
        database = postgres.databaseName,
        user = postgres.username,
        password = postgres.password
      )

      val resources =
        for {
          _ <- cats.effect.Resource.eval(Migrations.run[IO](config))
          orderStore <- OrderStore.postgres[IO](config)
          inventoryStore <- cats.effect.Resource.eval(InventoryStore.inMemory[IO])
          inventoryServer <- EmberServerBuilder
            .default[IO]
            .withHost(host"127.0.0.1")
            .withPort(port"0")
            .withHttpApp(InventoryRoutes.routes[IO](inventoryStore, NoOpLogger[IO]).orNotFound)
            .build
          httpClient <- HttpClient.resource[IO]
        } yield (orderStore, inventoryServer, httpClient)

      resources.use { case (orderStore, inventoryServer, httpClient) =>
        val inventoryBaseUri =
          Uri.unsafeFromString(s"http://127.0.0.1:${inventoryServer.address.getPort}")
        val inventoryClient = InventoryClient[IO](httpClient, inventoryBaseUri)
        val routes = OrderRoutes.routes[IO](orderStore, inventoryClient, NoOpLogger[IO])
        val request = Request[IO](Method.POST, uri"/orders")
          .withEntity(CreateOrderRequest("widget", 5))

        for {
          response <- routes.orNotFound.run(request)
          order <- response.as[OrderResponse]
        } yield {
          assertEquals(response.status, Status.Created)
          assertEquals(order.item, "widget")
          assertEquals(order.quantity, 5)
          assert(order.reservationId.nonEmpty)

          val conn = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
          try {
            val rs = conn
              .createStatement()
              .executeQuery(s"select item, quantity from orders where id = '${order.id}'")
            assert(rs.next(), "expected a persisted row for the created order")
            assertEquals(rs.getString("item"), "widget")
            assertEquals(rs.getInt("quantity"), 5)
          } finally conn.close()
        }
      }
    }
  }

  test("POST /orders returns a clean 5xx without leaking exception internals when Postgres is unreachable") {
    withContainers { postgres =>
      val unreachableConfig = PostgresConfig(
        host = postgres.host,
        port = postgres.mappedPort(5432) + 1, // nothing listens here
        database = postgres.databaseName,
        user = postgres.username,
        password = postgres.password
      )

      val resources =
        for {
          orderStore <- OrderStore.postgres[IO](unreachableConfig)
          inventoryStore <- cats.effect.Resource.eval(InventoryStore.inMemory[IO])
          inventoryServer <- EmberServerBuilder
            .default[IO]
            .withHost(host"127.0.0.1")
            .withPort(port"0")
            .withHttpApp(InventoryRoutes.routes[IO](inventoryStore, NoOpLogger[IO]).orNotFound)
            .build
          httpClient <- HttpClient.resource[IO]
        } yield (orderStore, inventoryServer, httpClient)

      resources.use { case (orderStore, inventoryServer, httpClient) =>
        val inventoryBaseUri =
          Uri.unsafeFromString(s"http://127.0.0.1:${inventoryServer.address.getPort}")
        val inventoryClient = InventoryClient[IO](httpClient, inventoryBaseUri)
        val routes = OrderRoutes.routes[IO](orderStore, inventoryClient, NoOpLogger[IO])
        val request = Request[IO](Method.POST, uri"/orders")
          .withEntity(CreateOrderRequest("widget", 5))

        for {
          response <- routes.orNotFound.run(request)
          body <- response.bodyText.compile.string
        } yield {
          assert(response.status.code >= 500, s"expected a 5xx status, got ${response.status}")
          assert(!body.contains("Exception"), s"response body leaked exception details: $body")
          assert(!body.toLowerCase.contains("skunk"), s"response body leaked Skunk internals: $body")
        }
      }
    }
  }
}
