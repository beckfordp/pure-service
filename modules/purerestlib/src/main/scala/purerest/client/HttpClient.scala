package purerest.client

import cats.effect.{Async, Resource}
import fs2.io.net.Network
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

object HttpClient {
  def resource[F[_]: Async: Network]: Resource[F, Client[F]] =
    EmberClientBuilder.default[F].build
}
