// Scala 3 version used to compile this project.
val scala3Version = "3.9.0"

// Shared dependency versions, referenced by modules as they adopt these
// libraries in later tracks.
val catsEffectVersion = "3.7.0"
val http4sVersion = "0.23.37"
val circeVersion = "0.14.16"
val munitVersion = "1.3.6"

// Settings shared by every module in this build.
lazy val commonSettings = Seq(
  scalaVersion := scala3Version,

  // munit: test framework used across this project (Scala-native, no JUnit dependency).
  libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
)

// purerest: the platform library providing cross-cutting microservice
// concerns (tracing, observability, resilience) as composable, annotation-free
// building blocks. order-service and inventory-service both depend on it.
lazy val purerest = project
  .in(file("modules/purerest"))
  .settings(commonSettings)
  .settings(
    name := "purerest"
  )

// order-service: REST API that places orders, calling inventory-service via
// purerest's client to reserve stock.
lazy val orderService = project
  .in(file("modules/order-service"))
  .dependsOn(purerest)
  .settings(commonSettings)
  .settings(
    name := "order-service"
  )

// inventory-service: REST API exposing stock reservation endpoints; a second,
// independent consumer of purerest.
lazy val inventoryService = project
  .in(file("modules/inventory-service"))
  .dependsOn(purerest)
  .settings(commonSettings)
  .settings(
    name := "inventory-service"
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
