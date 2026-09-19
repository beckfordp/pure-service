// Scala 3 version used to compile this project.
val scala3Version = "3.9.0"

// Single-module sbt project. Will grow into a multi-module build (purerest
// library + order-service + inventory-service) as those tracks land.
lazy val root = project
  .in(file("."))
  .settings(
    name := "pure-service",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      // munit: test framework used across this project (Scala-native, no JUnit dependency).
      "org.scalameta" %% "munit" % "1.3.6" % Test
    )
  )
