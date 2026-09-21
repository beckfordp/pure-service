package inventoryservice

import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import purerest.logging.Logging
import purerest.tracing.{ServerTracing, Tracing}

object Main extends IOApp.Simple {

  private val port: Port =
    sys.env.get("INVENTORY_SERVICE_PORT").flatMap(Port.fromString).getOrElse(port"8081")

  val run: IO[Unit] =
    Tracing.console[IO]("inventory-service").use { tracer =>
      for {
        logger <- Logging.create[IO](tracer, "inventory-service")
        store <- InventoryStore.inMemory[IO]
        routes = ServerTracing.middleware(tracer)(InventoryRoutes.routes[IO](store, logger))
        _ <- EmberServerBuilder
          .default[IO]
          .withHost(host"0.0.0.0")
          .withPort(port)
          .withHttpApp(routes.orNotFound)
          .build
          .useForever
      } yield ()
    }
}
