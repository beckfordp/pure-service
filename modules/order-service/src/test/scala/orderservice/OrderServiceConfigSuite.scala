package orderservice

import cats.effect.IO
import munit.CatsEffectSuite
import pureconfig.ConfigSource

class OrderServiceConfigSuite extends CatsEffectSuite {

  private val validHocon =
    """
      |port = 8080
      |metrics-port = 9090
      |inventory-service-base-url = "http://localhost:8081"
      |postgres {
      |  host = "localhost"
      |  port = 5432
      |  database = "orders"
      |  user = "orders"
      |  password = "orders"
      |}
      |""".stripMargin

  test("loads a fully-specified config") {
    val result = ConfigSource.string(validHocon).load[OrderServiceConfig]
    assertEquals(
      result,
      Right(
        OrderServiceConfig(
          port = 8080,
          metricsPort = 9090,
          inventoryServiceBaseUrl = "http://localhost:8081",
          postgres = PostgresConfig(
            host = "localhost",
            port = 5432,
            database = "orders",
            user = "orders",
            password = "orders"
          )
        )
      )
    )
  }

  test("fails to load when a required field is missing") {
    val missingPassword =
      """
        |port = 8080
        |metrics-port = 9090
        |inventory-service-base-url = "http://localhost:8081"
        |postgres {
        |  host = "localhost"
        |  port = 5432
        |  database = "orders"
        |  user = "orders"
        |}
        |""".stripMargin

    assert(ConfigSource.string(missingPassword).load[OrderServiceConfig].isLeft)
  }

  test("load[F] reads the shipped application.conf defaults") {
    OrderServiceConfig.load[IO].map { config =>
      assertEquals(config.port, 8080)
      assertEquals(config.metricsPort, 9090)
      assertEquals(config.inventoryServiceBaseUrl, "http://localhost:8081")
      assertEquals(
        config.postgres,
        PostgresConfig("localhost", 5432, "orders", "orders", "orders")
      )
    }
  }
}
