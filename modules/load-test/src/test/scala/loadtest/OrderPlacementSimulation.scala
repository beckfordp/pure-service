package loadtest

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

/** Repeatedly places orders against order-service's `POST /orders` — the call
  * path that drives order-service -> inventory-service via purerest's resilient
  * client (tracing, metrics, retry, circuit breaker). Run via
  * `sbt loadTest/Gatling/test` (not the default `test` task) against a running
  * order-service, or via `scripts/loadtest-purerest.sh`, which also
  * orchestrates a healthy pass and an induced-failure pass.
  *
  * `baseUrl` is overridable (`-DbaseUrl=...`) so this can point at a different
  * host/port without editing the simulation.
  */
class OrderPlacementSimulation extends Simulation {

  private val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  private val placeOrder =
    exec(
      http("POST /orders")
        .post("/orders")
        .body(StringBody("""{"item": "widget", "quantity": 1}"""))
    )

  private val scn = scenario("Place orders").exec(placeOrder)

  // Ramp to ~15 concurrent users over 30s, then hold at ~5 users/sec for a
  // further 60s (~90s total) — enough sustained traffic for RED-metric
  // histograms/counters (and, under induced failure, resilience counters)
  // to look like real distributions, without needing more than a laptop
  // running docker-compose.
  setUp(
    scn.inject(
      rampUsers(15).during(30.seconds),
      constantUsersPerSec(5).during(60.seconds)
    )
  ).protocols(httpProtocol)
}
