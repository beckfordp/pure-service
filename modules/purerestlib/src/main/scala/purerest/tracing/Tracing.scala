package purerest.tracing

import cats.effect.{Async, IO, Resource}
import cats.syntax.all._
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.oteljava.context.LocalContextProvider
import org.typelevel.otel4s.oteljava.testkit.trace.TracesTestkit
import org.typelevel.otel4s.trace.Tracer

object Tracing {

  /** A tracer backed by an in-memory span exporter, exposing captured spans —
    * for asserting on tracing behavior in tests.
    */
  final case class TestTracer[F[_]](
      tracer: Tracer[F],
      finishedSpans: F[List[SpanData]]
  )

  /** A tracer that exports spans to the console (stdout), for manual
    * verification when running a service locally. No real collector/backend is
    * configured.
    */
  def console[F[_]: {Async, LocalContextProvider}](
      instrumentationName: String
  ): Resource[F, Tracer[F]] =
    OtelJava
      .resource[F](
        Async[F].delay {
          val tracerProvider = SdkTracerProvider
            .builder()
            .addSpanProcessor(
              SimpleSpanProcessor.create(LoggingSpanExporter.create())
            )
            .build()
          OpenTelemetrySdk
            .builder()
            .setTracerProvider(tracerProvider)
            // W3C Trace Context propagator — without this, Tracer.propagate/joinOrRoot
            // are no-ops, since OpenTelemetrySdkBuilder defaults to no propagators.
            .setPropagators(
              ContextPropagators.create(W3CTraceContextPropagator.getInstance())
            )
            .build()
        }
      )
      .evalMap(_.tracerProvider.get(instrumentationName))

  /** A tracer backed by an in-memory span exporter, for asserting on captured
    * spans in tests.
    */
  def test[F[_]: {Async, LocalContextProvider}](
      instrumentationName: String
  ): Resource[F, TestTracer[F]] =
    // W3CTraceContextPropagator registered explicitly — TracesTestkit.inMemory
    // defaults to no propagators, which would make Tracer.propagate/joinOrRoot no-ops.
    TracesTestkit
      .inMemory[F](
        _.addTextMapPropagators(W3CTraceContextPropagator.getInstance())
      )
      .evalMap { testkit =>
        testkit.tracerProvider
          .get(instrumentationName)
          .map(TestTracer(_, testkit.finishedSpans))
      }

  /** Manual check: run via `sbt "purerest/runMain purerest.tracing.Tracing"`
    * and confirm a LoggingSpanExporter log line is printed for "demo-span".
    */
  def main(args: Array[String]): Unit = {
    given cats.effect.unsafe.IORuntime = cats.effect.unsafe.implicits.global
    console[IO]("manual-check")
      .use(_.span("demo-span").use(_ => IO.unit))
      .unsafeRunSync()
  }
}
