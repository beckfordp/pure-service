package purerest.tracing

import cats.data.OptionT
import cats.effect.Concurrent
import org.http4s.HttpRoutes
import org.typelevel.otel4s.trace.Tracer

object ServerTracing {

  /** Wraps `HttpRoutes[F]` with a span per handled request. If the inbound request
    * carries a W3C trace context in its headers, the span continues that trace;
    * otherwise it starts a new root trace.
    */
  def middleware[F[_]: Concurrent](tracer: Tracer[F])(routes: HttpRoutes[F]): HttpRoutes[F] =
    HttpRoutes[F] { req =>
      val carrier: Map[String, String] =
        req.headers.headers.map(h => h.name.toString -> h.value).toMap
      val spanName = s"${req.method.name} ${req.uri.path.renderString}"

      OptionT(
        tracer.joinOrRoot(carrier) {
          tracer.span(spanName).use(_ => routes.run(req).value)
        }
      )
    }
}
