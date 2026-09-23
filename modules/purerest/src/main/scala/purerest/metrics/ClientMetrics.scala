package purerest.metrics

import cats.effect.{Async, Clock, Resource}
import cats.syntax.all._
import org.http4s.client.Client
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter

import scala.concurrent.duration.SECONDS

object ClientMetrics {

  /** Wraps a `Client[F]` with a `http.client.request.duration` histogram
    * measurement (seconds) per outgoing call, tagged with the request method,
    * server address, and response status code (OTel semantic-convention
    * attribute names).
    */
  def middleware[F[_]: Async](meter: Meter[F])(client: Client[F]): Client[F] =
    Client[F] { req =>
      for {
        histogram <- Resource.eval(
          meter
            .histogram[Double]("http.client.request.duration")
            .withUnit("s")
            .create
        )
        start <- Resource.eval(Clock[F].monotonic)
        response <- client.run(req)
        end <- Resource.eval(Clock[F].monotonic)
        _ <- Resource.eval(
          histogram.record(
            (end - start).toUnit(SECONDS),
            List(
              Attribute("http.request.method", req.method.name),
              Attribute(
                "server.address",
                req.uri.authority.map(_.host.value).getOrElse("")
              ),
              Attribute(
                "http.response.status_code",
                response.status.code.toLong
              )
            )
          )
        )
      } yield response
    }
}
