package orderservice

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import org.http4s.{Method, Request, Status}
import org.typelevel.log4cats.noop.NoOpLogger
import purerest.tracing.{ServerTracing, Tracing}

class OrderRoutesSuite extends CatsEffectSuite {

  private val stubInventory: InventoryClient[IO] = new InventoryClient[IO] {
    def reserve(item: String, quantity: Int): IO[ReservationView] =
      IO.pure(ReservationView("stub-reservation-id", item, quantity))
  }

  test("POST /orders returns 201 with the order and inventory reservation id") {
    for {
      store <- OrderStore.inMemory[IO]
      routes = OrderRoutes.routes[IO](store, stubInventory, NoOpLogger[IO])
      request = Request[IO](Method.POST, uri"/orders")
        .withEntity(CreateOrderRequest("widget", 4))
      response <- routes.orNotFound.run(request)
      order <- response.as[OrderResponse]
    } yield {
      assertEquals(response.status, Status.Created)
      assertEquals(order.item, "widget")
      assertEquals(order.quantity, 4)
      assertEquals(order.reservationId, "stub-reservation-id")
    }
  }

  test("GET /orders/{id} returns 200 with the persisted order") {
    for {
      store <- OrderStore.inMemory[IO]
      routes = OrderRoutes.routes[IO](store, stubInventory, NoOpLogger[IO])
      postResponse <- routes.orNotFound.run(
        Request[IO](Method.POST, uri"/orders").withEntity(
          CreateOrderRequest("widget", 4)
        )
      )
      created <- postResponse.as[OrderResponse]
      getResponse <- routes.orNotFound.run(
        Request[IO](Method.GET, uri"/orders" / created.id)
      )
      fetched <- getResponse.as[OrderResponse]
    } yield {
      assertEquals(getResponse.status, Status.Ok)
      assertEquals(fetched, created)
    }
  }

  test(
    "GET /orders/{id} returns 404 with a JSON error body for an unknown id"
  ) {
    for {
      store <- OrderStore.inMemory[IO]
      routes = OrderRoutes.routes[IO](store, stubInventory, NoOpLogger[IO])
      response <- routes.orNotFound.run(
        Request[IO](Method.GET, uri"/orders" / "unknown-id")
      )
      body <- response.as[io.circe.Json]
    } yield {
      assertEquals(response.status, Status.NotFound)
      assert(
        body.asObject.exists(_.contains("error")),
        s"expected a JSON error body, got: $body"
      )
    }
  }

  test(
    "wrapped routes (with tracing middleware) record a span for a handled request"
  ) {
    Tracing.test[IO]("order-service-test").use { testTracer =>
      for {
        store <- OrderStore.inMemory[IO]
        routes = ServerTracing.middleware(testTracer.tracer)(
          OrderRoutes.routes[IO](store, stubInventory, NoOpLogger[IO])
        )
        request = Request[IO](Method.POST, uri"/orders")
          .withEntity(CreateOrderRequest("widget", 4))
        response <- routes.orNotFound.run(request)
        spans <- testTracer.finishedSpans
      } yield {
        assertEquals(response.status, Status.Created)
        assertEquals(spans.map(_.getName), List("POST /orders"))
      }
    }
  }
}
