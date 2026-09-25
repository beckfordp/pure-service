package purerest.metrics

import cats.effect.{Async, Resource}
import cats.syntax.all._
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.MetricData
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.oteljava.context.LocalContextProvider
import org.typelevel.otel4s.oteljava.testkit.metrics.MetricsTestkit
import org.typelevel.otel4s.metrics.Meter

/** Meter construction for purerest's Prometheus/OTel-based metrics. */
object Metrics {

  /** A meter backed by an in-memory metric reader, exposing collected metrics —
    * for asserting on metrics behavior in tests.
    */
  final case class TestMeter[F[_]](
      meter: Meter[F],
      collectMetrics: F[List[MetricData]]
  )

  /** A meter that exposes collected metrics on a Prometheus scrape endpoint
    * (`GET /metrics` on `port`), for manual verification and real scraping when
    * running a service locally. No OTLP/collector export is configured.
    */
  def oteljava[F[_]: {Async, LocalContextProvider}](
      instrumentationName: String,
      port: Int
  ): Resource[F, Meter[F]] =
    OtelJava
      .resource[F](
        Async[F].delay {
          val meterProvider = SdkMeterProvider
            .builder()
            .registerMetricReader(
              // Explicit "0.0.0.0": without it, the underlying Prometheus
              // exporter binds loopback-only, which is invisible to a
              // scraper running in a different container (or on a different
              // host) even though `curl localhost:<port>/metrics` from
              // *inside* the same container/process works fine — the gap
              // this track's real docker-compose network setup caught,
              // where curl-from-the-host-while-the-service-also-ran-on-the-
              // host (every prior verify-*.sh script) couldn't.
              PrometheusHttpServer
                .builder()
                .setHost("0.0.0.0")
                .setPort(port)
                .build()
            )
            .build()
          OpenTelemetrySdk
            .builder()
            .setMeterProvider(meterProvider)
            .build()
        }
      )
      .evalMap(_.meterProvider.get(instrumentationName))

  /** A meter backed by an in-memory metric reader, for asserting on collected
    * metrics in tests.
    */
  def test[F[_]: {Async, LocalContextProvider}](
      instrumentationName: String
  ): Resource[F, TestMeter[F]] =
    MetricsTestkit
      .inMemory[F]()
      .evalMap { testkit =>
        testkit.meterProvider
          .get(instrumentationName)
          .map(TestMeter(_, testkit.collectMetrics))
      }
}
