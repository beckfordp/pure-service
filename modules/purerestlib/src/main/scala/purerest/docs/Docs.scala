package purerest.docs

import cats.effect.Async
import org.http4s.HttpRoutes
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

/** Interprets a service's tapir endpoints into both the real `HttpRoutes[F]`
  * and a generated OpenAPI spec + browsable Swagger UI, from the same source of
  * truth — so routes and docs can't drift apart.
  */
object Docs {
  def routes[F[_]: Async](
      title: String,
      version: String,
      endpoints: List[ServerEndpoint[Any, F]]
  ): HttpRoutes[F] = {
    val swaggerEndpoints =
      SwaggerInterpreter().fromServerEndpoints[F](endpoints, title, version)
    Http4sServerInterpreter[F]().toRoutes(endpoints ++ swaggerEndpoints)
  }
}
