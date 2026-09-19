package inventoryservice

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import org.http4s.{Method, Request, Status}

class InventoryRoutesSuite extends CatsEffectSuite {

  test("POST /inventory/reserve returns 201 with a reservation") {
    for {
      store <- InventoryStore.inMemory[IO]
      routes = InventoryRoutes.routes[IO](store)
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

  test("unmatched routes return 404") {
    for {
      store <- InventoryStore.inMemory[IO]
      routes = InventoryRoutes.routes[IO](store)
      request = Request[IO](Method.GET, uri"/nope")
      response <- routes.orNotFound.run(request)
    } yield assertEquals(response.status, Status.NotFound)
  }
}
