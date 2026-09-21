package purerest.tracing

import cats.effect.{IO, Ref}
import munit.CatsEffectSuite
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{HttpApp, Request, Response}

class ClientTracingSuite extends CatsEffectSuite {

  test("wrapped client injects the active span's trace context into outgoing headers") {
    Tracing.test[IO]("purerest-client-test").use { testTracer =>
      for {
        receivedHeaders <- Ref.of[IO, Map[String, String]](Map.empty)
        stubApp: HttpApp[IO] = HttpApp { req =>
          receivedHeaders.set(req.headers.headers.map(h => h.name.toString -> h.value).toMap) *>
            Ok("pong")
        }
        client = Client.fromHttpApp(stubApp)
        wrapped = ClientTracing.middleware(testTracer.tracer)(client)
        _ <- testTracer.tracer.span("outbound-call").use { _ =>
          wrapped.run(Request[IO](uri = uri"/ping")).use_
        }
        headers <- receivedHeaders.get
      } yield assert(
        headers.contains("traceparent"),
        s"expected a traceparent header, got: $headers"
      )
    }
  }

  test("wrapped client is a no-op passthrough when no span is active") {
    Tracing.test[IO]("purerest-client-test").use { testTracer =>
      for {
        receivedHeaders <- Ref.of[IO, Map[String, String]](Map.empty)
        stubApp: HttpApp[IO] = HttpApp { req =>
          receivedHeaders.set(req.headers.headers.map(h => h.name.toString -> h.value).toMap) *>
            Ok("pong")
        }
        client = Client.fromHttpApp(stubApp)
        wrapped = ClientTracing.middleware(testTracer.tracer)(client)
        _ <- wrapped.run(Request[IO](uri = uri"/ping")).use_
        headers <- receivedHeaders.get
      } yield assert(!headers.contains("traceparent"), s"expected no traceparent header, got: $headers")
    }
  }
}
