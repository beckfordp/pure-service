package inventoryservice

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import org.http4s.{Method, Request, Status}
import org.typelevel.log4cats.noop.NoOpLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger.{INFO, WARN}
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

  test(
    "with induced failure rate 1.0, POST /inventory/reserve always returns 500"
  ) {
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

  test(
    "with a tiny positive induced failure rate, POST /inventory/reserve still usually succeeds"
  ) {
    // Exercises the branch where failureRate > 0 but the random roll doesn't trigger
    // a failure — distinct from the failureRate <= 0.0 short-circuit tested below.
    // Flake probability is astronomically low (~1e-7).
    for {
      store <- InventoryStore.inMemory[IO]
      routes = InventoryRoutes.routes[IO](
        store,
        NoOpLogger[IO],
        InducedFailureConfig(failureRate = 0.0000001, delay = Duration.Zero)
      )
      request = Request[IO](Method.POST, uri"/inventory/reserve")
        .withEntity(ReserveRequest("widget", 3))
      response <- routes.orNotFound.run(request)
    } yield assertEquals(response.status, Status.Created)
  }

  test(
    "with induced failure rate 0.0, POST /inventory/reserve still succeeds"
  ) {
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
      assert(
        (end - start) >= 200.millis,
        s"expected at least a 200ms delay, took ${end - start}"
      )
    }
  }

  test(
    "POST /inventory/reserve logs a received-request line and a completed line with structured context"
  ) {
    for {
      store <- InventoryStore.inMemory[IO]
      testLogger = StructuredTestingLogger.impl[IO]()
      routes = InventoryRoutes.routes[IO](store, testLogger)
      request = Request[IO](Method.POST, uri"/inventory/reserve")
        .withEntity(ReserveRequest("widget", 3))
      response <- routes.orNotFound.run(request)
      reservation <- response.as[Reservation]
      logged <- testLogger.logged
    } yield {
      val infos = logged.collect { case m: INFO => m }
      assert(
        infos.exists(m =>
          m.message.toLowerCase.contains("received") &&
            m.ctx.get("method").contains("POST") &&
            m.ctx.get("item").contains("widget")
        ),
        s"expected a received-request INFO line with method/item context, got: $infos"
      )
      assert(
        infos.exists(m =>
          m.message.toLowerCase.contains("completed") &&
            m.ctx.get("reservation_id").contains(reservation.id)
        ),
        s"expected a completed INFO line with reservation_id context, got: $infos"
      )
    }
  }

  test(
    "with induced failure rate 1.0, the induced-failure trigger logs a WARN with item/quantity context"
  ) {
    for {
      store <- InventoryStore.inMemory[IO]
      testLogger = StructuredTestingLogger.impl[IO]()
      routes = InventoryRoutes.routes[IO](
        store,
        testLogger,
        InducedFailureConfig(failureRate = 1.0, delay = Duration.Zero)
      )
      request = Request[IO](Method.POST, uri"/inventory/reserve")
        .withEntity(ReserveRequest("widget", 3))
      response <- routes.orNotFound.run(request)
      logged <- testLogger.logged
    } yield {
      assertEquals(response.status, Status.InternalServerError)
      val warns = logged.collect { case m: WARN => m }
      assert(
        warns.exists(m =>
          m.message.toLowerCase.contains("induced failure") &&
            m.ctx.get("item").contains("widget") &&
            m.ctx.get("quantity").contains("3")
        ),
        s"expected a WARN line for the induced failure with item/quantity context, got: $warns"
      )
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

  test(
    "wrapped routes (with tracing middleware) record a span for a handled request"
  ) {
    Tracing.test[IO]("inventory-service-test").use { testTracer =>
      for {
        store <- InventoryStore.inMemory[IO]
        routes = ServerTracing.middleware(testTracer.tracer)(
          InventoryRoutes.routes[IO](store, NoOpLogger[IO])
        )
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
