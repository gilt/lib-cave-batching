organization := "com.gilt"

name := "lib-cave-batching"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.11.7", "2.10.5")

libraryDependencies ++= Seq(
  "com.gilt" %% "gfc-logging" % "0.0.3",
  "com.gilt" %% "gfc-time" % "0.0.5",
  "com.typesafe" % "config" % "1.3.0",
  "joda-time" % "joda-time" % "2.8.2",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "org.mockito" % "mockito-core" % "1.8.5" % "test"
)

releaseCrossBuild := true

releasePublishArtifactsAction := PgpKeys.publishSigned.value

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("Apache-style" -> url("https://raw.githubusercontent.com/gilt/gfc-time/master/LICENSE"))

homepage := Some(url("https://github.com/gilt/gfc-time"))

pomExtra := (
  <scm>
    <url>https://github.com/gilt/gfc-time.git</url>
    <connection>scm:git:git@github.com:gilt/gfc-time.git</connection>
  </scm>
    <developers>
      <developer>
        <id>gheine</id>
        <name>Gregor Heine</name>
        <url>https://github.com/gheine</url>
      </developer>
      <developer>
        <id>ebowman</id>
        <name>Eric Bowman</name>
        <url>https://github.com/ebowman</url>
      </developer>
      <developer>
        <id>andreyk0</id>
        <name>Andrey Kartashov</name>
        <url>https://github.com/andreyk0</url>
      </developer>
    </developers>
  )

