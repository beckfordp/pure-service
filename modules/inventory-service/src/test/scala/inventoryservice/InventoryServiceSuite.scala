package inventoryservice

class InventoryServiceSuite extends munit.FunSuite {
  test("InventoryService.name identifies the module") {
    assertEquals(InventoryService.name, "inventory-service")
  }
}
