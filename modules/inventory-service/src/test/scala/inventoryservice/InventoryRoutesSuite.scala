package inventoryservice

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import org.http4s.{Method, Request, Status}
import org.typelevel.log4cats.noop.NoOpLogger
import purerest.tracing.{ServerTracing, Tracing}

import scala.concurrent.duration._

class InventoryRoutesSuite extends CatsEffectSuite {

  test("POST /inventory/reserve returns 201 with a reservation") {
    for {
      store <- InventoryStore.inMemory[IO]
      routes = InventoryRoutes.routes[IO](store, NoOpLogger[IO])
      request = Request[IO](Method.POST, uri"/inventory/reserve")
        .withEntity(ReserveRequest("widget", 3))
      response <- routes.orNotFound.run(request)
      reservation <- response.as[Reservation]
    } yield {
      assertEquals(response.status, Status.Created)
      assertEquals(reservation.item, "widget")
      assertEquals(reservation.quantity, 3)
    }
  }

  test("with induced failure rate 1.0, POST /inventory/reserve always returns 500") {
    for {
      store <- InventoryStore.inMemory[IO]
      routes = InventoryRoutes.routes[IO](
        store,
        NoOpLogger[IO],
        InducedFailureConfig(failureRate = 1.0, delay = Duration.Zero)
      )
      request = Request[IO](Method.POST, uri"/inventory/reserve")
        .withEntity(ReserveRequest("widget", 3))
      response <- routes.orNotFound.run(request)
    } yield assertEquals(response.status, Status.InternalServerError)
  }

  test("with induced failure rate 0.0, POST /inventory/reserve still succeeds") {
    for {
      store <- InventoryStore.inMemory[IO]
      routes = InventoryRoutes.routes[IO](
        store,
        NoOpLogger[IO],
        InducedFailureConfig(failureRate = 0.0, delay = Duration.Zero)
      )
      request = Request[IO](Method.POST, uri"/inventory/reserve")
        .withEntity(ReserveRequest("widget", 3))
      response <- routes.orNotFound.run(request)
    } yield assertEquals(response.status, Status.Created)
  }

  test("induced delay measurably delays the response") {
    for {
      store <- InventoryStore.inMemory[IO]
      routes = InventoryRoutes.routes[IO](
        store,
        NoOpLogger[IO],
        InducedFailureConfig(failureRate = 0.0, delay = 200.millis)
      )
      request = Request[IO](Method.POST, uri"/inventory/reserve")
        .withEntity(ReserveRequest("widget", 3))
      start <- IO.monotonic
      response <- routes.orNotFound.run(request)
      end <- IO.monotonic
    } yield {
      assertEquals(response.status, Status.Created)
      assert((end - start) >= 200.millis, s"expected at least a 200ms delay, took ${end - start}")
    }
  }

  test("unmatched routes return 404") {
    for {
      store <- InventoryStore.inMemory[IO]
      routes = InventoryRoutes.routes[IO](store, NoOpLogger[IO])
      request = Request[IO](Method.GET, uri"/nope")
      response <- routes.orNotFound.run(request)
    } yield assertEquals(response.status, Status.NotFound)
  }

  test("wrapped routes (with tracing middleware) record a span for a handled request") {
    Tracing.test[IO]("inventory-service-test").use { testTracer =>
      for {
        store <- InventoryStore.inMemory[IO]
        routes = ServerTracing.middleware(testTracer.tracer)(InventoryRoutes.routes[IO](store, NoOpLogger[IO]))
        request = Request[IO](Method.POST, uri"/inventory/reserve")
          .withEntity(ReserveRequest("widget", 3))
        response <- routes.orNotFound.run(request)
        spans <- testTracer.finishedSpans
      } yield {
        assertEquals(response.status, Status.Created)
        assertEquals(spans.map(_.getName), List("POST /inventory/reserve"))
      }
    }
  }
}
