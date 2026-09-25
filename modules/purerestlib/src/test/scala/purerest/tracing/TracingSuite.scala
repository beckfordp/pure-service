package purerest.tracing

import cats.effect.IO
import munit.CatsEffectSuite

class TracingSuite extends CatsEffectSuite {

  test("test tracer records a created span") {
    Tracing.test[IO]("purerest-test").use { testTracer =>
      for {
        _ <- testTracer.tracer.span("my-operation").use(_ => IO.unit)
        spans <- testTracer.finishedSpans
      } yield assertEquals(spans.map(_.getName), List("my-operation"))
    }
  }
}
