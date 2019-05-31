import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{dockerExposedPorts, dockerUsername}
import com.typesafe.sbt.packager.docker.{Cmd, DockerPermissionStrategy}
name := "MediaCensus"
 
version := "1.0"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

scalaVersion := "2.12.3"

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )

lazy val commonSettings = Seq(
  version := "1.0",
  scalaVersion := "2.12.3"
)

lazy val `mediacensus` = (project in file(".")).enablePlugins(PlayScala, DockerPlugin, AshScriptPlugin)
  .dependsOn(common)
  .aggregate(common)
  .settings(version := sys.props.getOrElse("build.number","DEV"),
    dockerExposedPorts := Seq(9000),
    dockerUsername  := sys.props.get("docker.username"),
    dockerRepository := Some("guardianmultimedia"),
    packageName in Docker := "guardianmultimedia/mediacensus-webapp",
    packageName := "mediacensus",
    dockerBaseImage := "openjdk:8-jdk-alpine",
    dockerAlias := docker.DockerAlias(None,Some("guardianmultimedia"),"mediacensus-webapp",Some(sys.props.getOrElse("build.number","DEV"))),
    dockerCommands ++= Seq(

    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-java8" % circeVersion,
      "com.dripower" %% "play-circe" % "2610.0"
    )
  )

val akkaVersion = "2.5.22"
val circeVersion = "0.9.3"
val slf4jVersion = "1.7.25"

lazy val `common` = (project in file("common"))
  .settings(commonSettings, libraryDependencies ++= Seq(
    "org.postgresql" % "postgresql" % "42.2.5",
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-java8" % circeVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "org.slf4j" % "slf4j-simple" % slf4jVersion,
    "com.softwaremill.sttp" %% "core" % "0.0.20",
    "com.softwaremill.sttp" %% "async-http-client-backend-future" % "0.0.20",
    "org.asynchttpclient" % "async-http-client" % "2.0.37",
    "com.softwaremill.sttp" %% "akka-http-backend" % "0.0.20",
    "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
    specs2
  ))

lazy val `cronscanner` = (project in file("cronscanner"))
  .enablePlugins(DockerPlugin, AshScriptPlugin)
    .dependsOn(common)
    .settings(commonSettings, libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % "42.2.5",
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-java8" % circeVersion,
      jdbc
    ),version := sys.props.getOrElse("build.number","DEV"),
      dockerPermissionStrategy := DockerPermissionStrategy.Run,
      daemonUserUid in Docker := None,
      daemonUser in Docker := "daemon",
      dockerUsername  := sys.props.get("docker.username"),
      dockerRepository := Some("guardianmultimedia"),
      packageName in Docker := "guardianmultimedia/mediacensus-scanner",
      packageName := "mediacensus",
      dockerBaseImage := "openjdk:8-jdk-alpine",
      dockerAlias := docker.DockerAlias(None,Some("guardianmultimedia"),"mediacensus-scanner",Some(sys.props.getOrElse("build.number","DEV"))),
      dockerCommands ++= Seq(
        Cmd("USER","root"),
        Cmd("RUN", "chmod -R a+x /opt/docker"),
        Cmd("USER", "daemon")
      )
    )


val elastic4sVersion = "6.0.4"
libraryDependencies ++= Seq (
  "com.sksamuel.elastic4s" %% "elastic4s-http" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-circe" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion % "test",
  "com.sksamuel.elastic4s" %% "elastic4s-embedded" % elastic4sVersion % "test",
)

libraryDependencies ++= Seq(

)

unmanagedResourceDirectories in Test +=  { baseDirectory ( _ /"target/web/public/test" ).value }

enablePlugins(DockerPlugin, AshScriptPlugin)