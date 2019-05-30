import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{dockerExposedPorts, dockerUsername}

name := "MediaCensus"
 
version := "1.0" 
      
lazy val `mediacensus` = (project in file(".")).enablePlugins(PlayScala)
  .settings(version := sys.props.getOrElse("build.number","DEV"),
    dockerExposedPorts := Seq(9000),
    dockerUsername  := sys.props.get("docker.username"),
    dockerRepository := Some("guardianmultimedia"),
    packageName in Docker := "guardianmultimedia/mediacensus",
    packageName := "mediacensus",
    dockerBaseImage := "openjdk:8-jdk-alpine",
    dockerAlias := docker.DockerAlias(None,Some("guardianmultimedia"),"mediacensus",Some(sys.props.getOrElse("build.number","DEV"))),
    dockerCommands ++= Seq(

    ))

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
      
resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
      
scalaVersion := "2.12.3"

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.5"

val elastic4sVersion = "6.0.4"
libraryDependencies ++= Seq (
  "com.sksamuel.elastic4s" %% "elastic4s-http" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-circe" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion % "test",
  "com.sksamuel.elastic4s" %% "elastic4s-embedded" % elastic4sVersion % "test",
)
val circeVersion = "0.9.3"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-java8" % circeVersion,
  "com.dripower" %% "play-circe" % "2610.0"
)

unmanagedResourceDirectories in Test +=  { baseDirectory ( _ /"target/web/public/test" ).value }

enablePlugins(DockerPlugin, AshScriptPlugin)