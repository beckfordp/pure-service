package orderservice

import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import org.http4s.Uri
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import purerest.client.HttpClient
import purerest.logging.Logging
import purerest.tracing.{ClientTracing, ServerTracing, Tracing}

object Main extends IOApp.Simple {

  private val port: Port =
    sys.env.get("ORDER_SERVICE_PORT").flatMap(Port.fromString).getOrElse(port"8080")

  private val inventoryServiceBaseUri: Uri =
    sys.env
      .get("INVENTORY_SERVICE_BASE_URL")
      .flatMap(Uri.fromString(_).toOption)
      .getOrElse(uri"http://localhost:8081")

  val run: IO[Unit] =
    Tracing.console[IO]("order-service").use { tracer =>
      for {
        logger <- Logging.create[IO](tracer, "order-service")
        store <- OrderStore.inMemory[IO]
        _ <- HttpClient.resource[IO].use { httpClient =>
          val tracedClient = ClientTracing.middleware(tracer)(httpClient)
          val inventory = InventoryClient[IO](tracedClient, inventoryServiceBaseUri)
          val routes = ServerTracing.middleware(tracer)(OrderRoutes.routes[IO](store, inventory, logger))
          EmberServerBuilder
            .default[IO]
            .withHost(host"0.0.0.0")
            .withPort(port)
            .withHttpApp(routes.orNotFound)
            .build
            .useForever
        }
      } yield ()
    }
}
