package purerest.metrics

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{HttpRoutes, Request, Status}
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.oteljava.testkit.metrics.{
  MetricExpectation,
  MetricExpectations,
  PointExpectation
}

class ServerMetricsSuite extends CatsEffectSuite {

  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "ping" =>
      Ok("pong")
  }

  test(
    "wrapped routes record a request duration measurement for a handled request"
  ) {
    Metrics.test[IO]("purerest-server-metrics-test").use { testMeter =>
      for {
        wrapped <- IO.pure(ServerMetrics.middleware(testMeter.meter)(routes))
        response <- wrapped.orNotFound.run(Request[IO](uri = uri"/ping"))
        metrics <- testMeter.collectMetrics
      } yield {
        assertEquals(response.status, Status.Ok)
        MetricExpectations.checkAll(
          metrics,
          MetricExpectation
            .histogram("http.server.request.duration")
            .containsPoints(
              PointExpectation.histogram
                .attributesSubset(
                  Attribute("http.request.method", "GET"),
                  Attribute("http.route", "/ping"),
                  Attribute("http.response.status_code", 200L)
                )
            )
        ) match {
          case Right(_)         => ()
          case Left(mismatches) => fail(MetricExpectations.format(mismatches))
        }
      }
    }
  }

  test("wrapped routes still return 404 for unmatched paths") {
    Metrics.test[IO]("purerest-server-metrics-test").use { testMeter =>
      val wrapped = ServerMetrics.middleware(testMeter.meter)(routes)
      for {
        response <- wrapped.orNotFound.run(Request[IO](uri = uri"/nope"))
      } yield assertEquals(response.status, Status.NotFound)
    }
  }
}
