package orderservice

import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import org.http4s.Uri
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import purerest.client.HttpClient

object Main extends IOApp.Simple {

  private val port: Port =
    sys.env.get("ORDER_SERVICE_PORT").flatMap(Port.fromString).getOrElse(port"8080")

  private val inventoryServiceBaseUri: Uri =
    sys.env
      .get("INVENTORY_SERVICE_BASE_URL")
      .flatMap(Uri.fromString(_).toOption)
      .getOrElse(uri"http://localhost:8081")

  val run: IO[Unit] =
    OrderStore.inMemory[IO].flatMap { store =>
      HttpClient.resource[IO].use { httpClient =>
        val inventory = InventoryClient[IO](httpClient, inventoryServiceBaseUri)
        EmberServerBuilder
          .default[IO]
          .withHost(host"0.0.0.0")
          .withPort(port)
          .withHttpApp(OrderRoutes.routes[IO](store, inventory).orNotFound)
          .build
          .useForever
      }
    }
}
