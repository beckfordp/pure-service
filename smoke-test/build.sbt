// A genuinely standalone sbt build — deliberately NOT referenced anywhere in the
// root purerest build.sbt, so `sbt projects`/`sbt compile`/`sbt test` at the
// repo root never sweeps this in. Its whole point is to simulate a real external
// consumer of purerest: a jar resolved from the local Ivy2 cache via an ordinary
// libraryDependencies entry, not the internal .dependsOn(purerestlib) project
// reference the main build's own order-service/inventory-service fixtures use.

// Read from -DpurerestVersion=<version>, never hardcoded: purerest's version is
// derived from git (sbt-dynver) in the main build and changes on every commit.
// See scripts/verify-purerest-consumption.sh, which resolves and passes this in.
val purerestVersion = sys.props.getOrElse(
  "purerestVersion",
  sys.error(
    "purerestVersion system property not set — run via " +
      "scripts/verify-purerest-consumption.sh, or pass -DpurerestVersion=<version> " +
      "(the output of `sbt purerestlib/version` in the main repo, after " +
      "`sbt purerestlib/publishLocal`)."
  )
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "purerest-consumer-smoke-test",
    scalaVersion := "3.9.0",
    libraryDependencies ++= Seq(
      // The whole point of this build: purerest resolved as an ordinary published
      // artifact from the local Ivy2 cache, not this repo's internal ProjectRef.
      "io.github.beckfordp" %% "purerestlib" % purerestVersion,
      "org.scalameta" %% "munit" % "1.3.6" % Test,
      "org.typelevel" %% "munit-cats-effect" % "2.2.1" % Test
    )
  )
