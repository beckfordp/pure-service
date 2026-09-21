// Scala 3 version used to compile this project.
val scala3Version = "3.9.0"

// Shared dependency versions, referenced by modules as they adopt these
// libraries in later tracks.
val catsEffectVersion = "3.7.0"
val http4sVersion = "0.23.37"
val circeVersion = "0.14.16"
val munitVersion = "1.3.6"
val munitCatsEffectVersion = "2.2.1"
val otel4sVersion = "1.1.0"
val openTelemetryVersion = "1.66.0"
val log4catsVersion = "2.8.0"

// Settings shared by every module in this build.
lazy val commonSettings = Seq(
  scalaVersion := scala3Version,

  libraryDependencies ++= Seq(
    // munit: test framework used across this project (Scala-native, no JUnit dependency).
    "org.scalameta" %% "munit" % munitVersion % Test,
    // munit-cats-effect: lets test bodies return IO[Unit] and run under munit directly.
    "org.typelevel" %% "munit-cats-effect" % munitCatsEffectVersion % Test
  )
)

// purerest: the platform library providing cross-cutting microservice
// concerns (tracing, observability, resilience) as composable, annotation-free
// building blocks. order-service and inventory-service both depend on it.
lazy val purerest = project
  .in(file("modules/purerest"))
  .settings(commonSettings)
  .settings(
    name := "purerest",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      // otel4s (oteljava backend): tracing API + a real OpenTelemetry Java SDK
      // underneath, giving us real exporters (console, and in-memory for tests).
      "org.typelevel" %% "otel4s-oteljava" % otel4sVersion,
      // Console/logging span exporter, for manual verification when running a
      // service locally.
      "io.opentelemetry" % "opentelemetry-exporter-logging" % openTelemetryVersion,
      // In-memory span exporter/testkit. Deliberately a normal compile dependency
      // (not Test-scoped): purerest.tracing.Tracing.test is a public testing helper
      // consumed by order-service's and inventory-service's own test suites, not
      // just purerest's — Test-scope deps don't propagate to consuming modules.
      "org.typelevel" %% "otel4s-oteljava-testkit" % otel4sVersion,
      // Only used to stand up a stub server in purerest's own tests — purerest's
      // main code has no server dependency.
      "org.http4s" %% "http4s-ember-server" % http4sVersion % Test,
      // Only used to build routes in purerest's own middleware tests.
      "org.http4s" %% "http4s-dsl" % http4sVersion % Test,
      // log4cats: structured, tagless-final logging.
      "org.typelevel" %% "log4cats-core" % log4catsVersion,
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
      // SLF4J binding — without one, log lines are silently dropped (NOP logger).
      // Logback (not slf4j-simple) because its pattern layout can render MDC
      // values (%X{trace_id}/%X{span_id}) — log4cats-slf4j pushes our per-call
      // context Map into SLF4J's MDC around each log statement, and slf4j-simple's
      // fixed layout has no way to display it. Runtime-only: never referenced
      // directly in code; configured via logback.xml.
      "ch.qos.logback" % "logback-classic" % "1.6.3" % Runtime,
      // In-memory capturing logger, for asserting on log output in tests.
      "org.typelevel" %% "log4cats-testing" % log4catsVersion % Test
    )
  )

// order-service: REST API that places orders, calling inventory-service via
// purerest's client to reserve stock. Depends on inventoryService in Test
// scope only, to run a real inventory-service in-process for integration
// tests — main code has no dependency on inventory-service.
lazy val orderService = project
  .in(file("modules/order-service"))
  .dependsOn(purerest, inventoryService % Test)
  .settings(commonSettings)
  .settings(
    name := "order-service",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion
    )
  )

// inventory-service: REST API exposing stock reservation endpoints; a second,
// independent consumer of purerest.
lazy val inventoryService = project
  .in(file("modules/inventory-service"))
  .dependsOn(purerest)
  .settings(commonSettings)
  .settings(
    name := "inventory-service",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion
    )
  )

// root: aggregates the modules so `sbt compile`/`sbt test` run across all of
// them; not published itself.
lazy val root = project
  .in(file("."))
  .aggregate(purerest, orderService, inventoryService)
  .settings(
    name := "pure-service",
    publish / skip := true
  )
