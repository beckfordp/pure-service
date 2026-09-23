package orderservice

import munit.FunSuite
import pureconfig.ConfigSource

class OrderServiceConfigSuite extends FunSuite {

  private val validHocon =
    """
      |port = 8080
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
}
