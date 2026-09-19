val scala3Version = "3.9.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "pure-service",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "1.3.6" % Test
  )
