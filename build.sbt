import scoverage.ScoverageSbtPlugin.ScoverageKeys

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(coverageSettings: _*)
  .settings(releaseSettings: _*)


lazy val commonSettings = Seq(
  organization := "com.gilt",
  name := "lib-cave-batching",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.11.8", "2.10.6"),
  libraryDependencies ++= Seq(
    "com.gilt" %% "gfc-concurrent" % "0.3.3",
    "joda-time" % "joda-time" % "2.9.4",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "org.mockito" % "mockito-core" % "1.8.5" % "test"
  )
)

lazy val coverageSettings = Seq(
  ScoverageKeys.coverageExcludedPackages := "com.gilt.cavellc;com.gilt.cavellc.models"
)

lazy val releaseSettings = Seq(
  scalacOptions += "-target:jvm-1.7",
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  licenses := Seq("Apache-style" -> url("https://raw.githubusercontent.com/gilt/lib-cave-batching/master/LICENSE")),
  homepage := Some(url("https://github.com/gilt/lib-cave-batching")),
  pomExtra := <scm>
                <url>https://github.com/gilt/lib-cave-batching.git</url>
                <connection>scm:git:git@github.com:gilt/lib-cave-batching.git</connection>
              </scm>
                <developers>
                  <developer>
                    <id>gheine</id>
                    <name>Gregor Heine</name>
                    <url>https://github.com/gheine</url>
                  </developer>
                  <developer>
                    <id>vdumitrescu</id>
                    <name>Val Dumitrescu</name>
                    <url>https://github.com/vdumitrescu</url>
                  </developer>
                </developers>
)
