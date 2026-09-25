package purerest.tracing

import cats.effect.{Concurrent, Resource}
import cats.syntax.all._
import org.http4s.{Header, Headers}
import org.http4s.client.Client
import org.typelevel.ci.CIString
import org.typelevel.otel4s.trace.Tracer

/** Client-side trace-context propagation middleware for http4s. */
object ClientTracing {

  /** Wraps a `Client[F]` so that outgoing requests carry the current span's
    * trace context (W3C headers) injected, if a span is active. A no-op
    * passthrough otherwise.
    */
  def middleware[F[_]: Concurrent](
      tracer: Tracer[F]
  )(client: Client[F]): Client[F] =
    Client[F] { req =>
      Resource.eval(tracer.propagate(Map.empty[String, String])).flatMap {
        carrier =>
          val traceHeaders = carrier.toList.map { case (k, v) =>
            Header.Raw(CIString(k), v)
          }
          client.run(req.withHeaders(req.headers ++ Headers(traceHeaders)))
      }
    }
}
