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

object Metrics {

  /** A meter backed by an in-memory metric reader, exposing collected metrics — for
    * asserting on metrics behavior in tests.
    */
  final case class TestMeter[F[_]](meter: Meter[F], collectMetrics: F[List[MetricData]])

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
            .registerMetricReader(PrometheusHttpServer.builder().setPort(port).build())
            .build()
          OpenTelemetrySdk
            .builder()
            .setMeterProvider(meterProvider)
            .build()
        }
      )
      .evalMap(_.meterProvider.get(instrumentationName))

  /** A meter backed by an in-memory metric reader, for asserting on collected metrics
    * in tests.
    */
  def test[F[_]: {Async, LocalContextProvider}](instrumentationName: String): Resource[F, TestMeter[F]] =
    MetricsTestkit
      .inMemory[F]()
      .evalMap { testkit =>
        testkit.meterProvider.get(instrumentationName).map(TestMeter(_, testkit.collectMetrics))
      }
}
