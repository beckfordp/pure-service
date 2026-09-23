package orderservice

import cats.effect.IO
import munit.CatsEffectSuite

class OrderStoreSuite extends CatsEffectSuite {

  test("create returns an order for the requested item and quantity") {
    for {
      store <- OrderStore.inMemory[IO]
      order <- store.create("widget", 2, "reservation-1", 2)
    } yield {
      assertEquals(order.item, "widget")
      assertEquals(order.quantity, 2)
      assert(order.id.nonEmpty)
    }
  }

  test("create produces distinct ids across calls") {
    for {
      store <- OrderStore.inMemory[IO]
      first <- store.create("widget", 1, "reservation-1", 1)
      second <- store.create("widget", 1, "reservation-2", 1)
    } yield assertNotEquals(first.id, second.id)
  }
}
