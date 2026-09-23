package purerest.metrics

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{HttpApp, Request}
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.oteljava.testkit.metrics.{MetricExpectation, MetricExpectations, PointExpectation}

class ClientMetricsSuite extends CatsEffectSuite {

  private val stubApp: HttpApp[IO] = HttpApp { _ => Ok("pong") }

  test("wrapped client records a request duration measurement for a completed call") {
    Metrics.test[IO]("purerest-client-metrics-test").use { testMeter =>
      val client = Client.fromHttpApp(stubApp)
      val wrapped = ClientMetrics.middleware(testMeter.meter)(client)
      for {
        _ <- wrapped.run(Request[IO](uri = uri"/ping")).use_
        metrics <- testMeter.collectMetrics
      } yield MetricExpectations.checkAll(
        metrics,
        MetricExpectation
          .histogram("http.client.request.duration")
          .containsPoints(
            PointExpectation.histogram
              .attributesSubset(
                Attribute("http.request.method", "GET"),
                Attribute("http.response.status_code", 200L)
              )
          )
      ) match {
        case Right(_)          => ()
        case Left(mismatches) => fail(MetricExpectations.format(mismatches))
      }
    }
  }
}
