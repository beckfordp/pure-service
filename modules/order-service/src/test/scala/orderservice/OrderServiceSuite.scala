package orderservice

class OrderServiceSuite extends munit.FunSuite {
  test("OrderService.name identifies the module") {
    assertEquals(OrderService.name, "order-service")
  }
}
