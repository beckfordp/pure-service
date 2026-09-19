package purerest

class PurerestSuite extends munit.FunSuite {
  test("Purerest.name identifies the module") {
    assertEquals(Purerest.name, "purerest")
  }
}
