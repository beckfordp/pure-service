package orderservice

import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import org.http4s.Uri
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import purerest.client.HttpClient
import purerest.docs.Docs
import purerest.logging.Logging
import purerest.tracing.{ClientTracing, ServerTracing, Tracing}

object Main extends IOApp.Simple {

  val run: IO[Unit] =
    for {
      config <- OrderServiceConfig.load[IO]
      port <- IO.fromOption(Port.fromInt(config.port))(
        new IllegalArgumentException(s"Invalid order-service port: ${config.port}")
      )
      inventoryServiceBaseUri <- IO.fromEither(Uri.fromString(config.inventoryServiceBaseUrl))
      _ <- Migrations.run[IO](config.postgres)
      _ <- Tracing.console[IO]("order-service").use { tracer =>
        for {
          logger <- Logging.create[IO](tracer, "order-service")
          store <- OrderStore.inMemory[IO]
          _ <- HttpClient.resource[IO].use { httpClient =>
            val tracedClient = ClientTracing.middleware(tracer)(httpClient)
            val inventory = InventoryClient[IO](tracedClient, inventoryServiceBaseUri)
            val docsRoutes = Docs.routes[IO](
              "Order Service",
              "1.0",
              List(OrderRoutes.serverEndpoint[IO](store, inventory, logger))
            )
            val routes = ServerTracing.middleware(tracer)(docsRoutes)
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
    } yield ()
}
