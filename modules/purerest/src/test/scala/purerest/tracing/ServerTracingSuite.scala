package purerest.tracing

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{HttpRoutes, Request, Status}

class ServerTracingSuite extends CatsEffectSuite {

  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root / "ping" =>
    Ok("pong")
  }

  test("wrapped routes record a span for a handled request") {
    Tracing.test[IO]("purerest-server-test").use { testTracer =>
      val wrapped = ServerTracing.middleware(testTracer.tracer)(routes)
      for {
        response <- wrapped.orNotFound.run(Request[IO](uri = uri"/ping"))
        spans <- testTracer.finishedSpans
      } yield {
        assertEquals(response.status, Status.Ok)
        assertEquals(spans.map(_.getName), List("GET /ping"))
      }
    }
  }

  test("wrapped routes still return 404 for unmatched paths") {
    Tracing.test[IO]("purerest-server-test").use { testTracer =>
      val wrapped = ServerTracing.middleware(testTracer.tracer)(routes)
      for {
        response <- wrapped.orNotFound.run(Request[IO](uri = uri"/nope"))
      } yield assertEquals(response.status, Status.NotFound)
    }
  }
}
