import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import models.AssetSweeperFile
import streamComponents._
import play.api.{Configuration, Logger}
import com.google.inject.Guice
import config.DatabaseConfiguration
import helpers.JdbcConnectionManager
import io.circe.syntax._
import io.circe.generic.auto._
import scala.concurrent.Await
import scala.concurrent.duration._

object CronScanner extends ZonedDateTimeEncoder {
  val logger = Logger(getClass)

  private val injector = Guice.createInjector(new Module())

  private val actorSystem = ActorSystem("CronScanner")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)
//  lazy val configurationFromEnvironment = Configuration.from(Map(
//    "assetSweeper.driver"->sys.env.getOrElse("ASSETSWEEPER_JDBC_DRIVER","org.postgresql.Driver"),
//    "assetSweeper.jdbcUrl"->sys.env("ASSETSWEEPER_JDBC_URL"),
//    "assetSweeper.user"->sys.env("ASSETSWEEPER_JDBC_USER"),
//    "assetSweeper.password"->sys.env("ASSETSWEEPER_PASSWORD")
//  ))

  lazy val assetSweeperConfig = DatabaseConfiguration(
    sys.env.getOrElse("ASSETSWEEPER_JDBC_DRIVER","org.postgresql.Driver"),
    sys.env("ASSETSWEEPER_JDBC_URL"),
    sys.env("ASSETSWEEPER_JDBC_USER"),
    sys.env("ASSETSWEEPER_PASSWORD")
  )

  def buildStream = {
    val sink = Sink.seq[AssetSweeperFile]

    GraphDSL.create(sink) { implicit builder=> { streamSink =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val srcFactory = new AssetSweeperFilesSource(assetSweeperConfig)
      val streamSource = builder.add(srcFactory)
      val ignoresFilter = builder.add(new FilterOutIgnores)
      streamSource ~> ignoresFilter ~> streamSink

      ClosedShape
    }}
  }


  def main(args: Array[String]): Unit = {
    val resultFuture = RunnableGraph.fromGraph(buildStream).run()

    val result = Await.result(resultFuture, 2 hours)

    val finalResultJson = result.asJson.toString()
  }
}
