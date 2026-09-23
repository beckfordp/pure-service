package purerest.metrics

import cats.effect.IO
import munit.CatsEffectSuite

class MetricsSuite extends CatsEffectSuite {

  test("test meter records a manually-emitted counter value") {
    Metrics.test[IO]("purerest-test").use { testMeter =>
      for {
        counter <- testMeter.meter.counter[Long]("my-counter").create
        _ <- counter.add(1L)
        metrics <- testMeter.collectMetrics
      } yield assertEquals(metrics.map(_.getName), List("my-counter"))
    }
  }
}
