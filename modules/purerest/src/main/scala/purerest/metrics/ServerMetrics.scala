package purerest.metrics

import cats.data.OptionT
import cats.effect.{Async, Clock}
import cats.syntax.all._
import org.http4s.HttpRoutes
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter

import scala.concurrent.duration.SECONDS

object ServerMetrics {

  /** Wraps `HttpRoutes[F]` with a `http.server.request.duration` histogram
    * measurement (seconds) per handled request, tagged with the request method,
    * route, and — when the route matched — the response status code (OTel
    * semantic-convention attribute names).
    */
  def middleware[F[_]: Async](meter: Meter[F])(routes: HttpRoutes[F]): HttpRoutes[F] =
    HttpRoutes[F] { req =>
      OptionT(
        for {
          histogram <- meter.histogram[Double]("http.server.request.duration").withUnit("s").create
          start <- Clock[F].monotonic
          maybeResponse <- routes.run(req).value
          end <- Clock[F].monotonic
          attributes = List(
            Attribute("http.request.method", req.method.name),
            Attribute("http.route", req.uri.path.renderString)
          ) ++ maybeResponse.map(r => Attribute("http.response.status_code", r.status.code.toLong)).toList
          _ <- histogram.record((end - start).toUnit(SECONDS), attributes)
        } yield maybeResponse
      )
    }
}
