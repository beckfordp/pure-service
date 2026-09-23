package inventoryservice

import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import purerest.docs.Docs
import purerest.logging.Logging
import purerest.tracing.{ServerTracing, Tracing}

import scala.concurrent.duration.{Duration, DurationLong}

object Main extends IOApp.Simple {

  private val port: Port =
    sys.env
      .get("INVENTORY_SERVICE_PORT")
      .flatMap(Port.fromString)
      .getOrElse(port"8081")

  private val inducedFailure: InducedFailureConfig =
    InducedFailureConfig(
      failureRate = sys.env
        .get("INVENTORY_INDUCED_FAILURE_RATE")
        .flatMap(_.toDoubleOption)
        .getOrElse(0.0),
      delay = sys.env
        .get("INVENTORY_INDUCED_DELAY_MS")
        .flatMap(_.toLongOption)
        .map(_.millis)
        .getOrElse(Duration.Zero)
    )

  val run: IO[Unit] =
    Tracing.console[IO]("inventory-service").use { tracer =>
      for {
        logger <- Logging.create[IO](tracer, "inventory-service")
        store <- InventoryStore.inMemory[IO]
        docsRoutes = Docs.routes[IO](
          "Inventory Service",
          "1.0",
          List(
            InventoryRoutes.serverEndpoint[IO](store, logger, inducedFailure)
          )
        )
        routes = ServerTracing.middleware(tracer)(docsRoutes)
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
