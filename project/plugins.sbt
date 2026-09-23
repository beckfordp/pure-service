addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
// Derives purerest's version from git tags/commits (e.g. 0.1.0-3-abc1234) instead of
// a manually-maintained val, so it can be published without hand-bumping a version
// on every release.
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
