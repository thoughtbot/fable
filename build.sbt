import ReleaseTransformations._

val catsEffectVersion = "[1.0.0,1.1.0)"
val catsVersion = "[1.4.0,1.5.0)"
val fs2Version = "[1.0.0,1.1.0)"
val kafkaVersion = "[2.1.0,2.2.0)"
val log4CatsVersion = "[0.2.0,0.3.0)"
val pureConfigVersion = "[0.10.0,0.11.0)"
val scalaTestVersion = "[3.0.5, 3.1.0)"

lazy val fable = (project in file("."))
  .settings(
    inThisBuild(List(scalaVersion := "2.12.8")),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-language:higherKinds",
      "-unchecked",
      "-Xfatal-warnings",
      "-Ypartial-unification",
      "-Ywarn-unused-import"
    ),
    autoAPIMappings := true,
    developers := List(
      Developer(
        id = "jferris",
        name = "Joe Ferris",
        email = "jferris@thoughtbot.com",
        url = url("https://github.com/jferris")
      )
    ),
    licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
    homepage := Some(url("https://github.com/thoughtbot/fable")),
    name := "fable",
    organization := "com.thoughtbot",
    publishMavenStyle := true,
    publishTo := Some(
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging
    ),
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/thoughtbot/fable"),
        "scm:git:git@github.com:thoughtbot/fable.git"
      )
    ),
    useGpg := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      releaseStepCommand("sonatypeRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    ),
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "com.github.pureconfig" %% "pureconfig" % pureConfigVersion,
      "io.chrisdavenport" %% "log4cats-slf4j" % log4CatsVersion,
      "org.apache.kafka" % "kafka-clients" % kafkaVersion,
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Test
    )
  )
