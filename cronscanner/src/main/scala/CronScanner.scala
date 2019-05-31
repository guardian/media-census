import akka.actor.ActorSystem
import akka.event.DiagnosticLoggingAdapter
import akka.event.Logging
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import com.softwaremill.sttp.Uri
import models.AssetSweeperFile
import streamComponents._
import play.api.{Configuration, Logger}
import config.{DatabaseConfiguration, VSConfig}
import io.circe.syntax._
import io.circe.generic.auto._
import vidispineclient.VSPathMapper
import com.softwaremill.sttp._
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object CronScanner extends ZonedDateTimeEncoder {
  val logger = Logger(getClass)

  private implicit val actorSystem = ActorSystem("CronScanner")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)

  lazy val assetSweeperConfig = DatabaseConfiguration(
    sys.env.getOrElse("ASSETSWEEPER_JDBC_DRIVER","org.postgresql.Driver"),
    sys.env("ASSETSWEEPER_JDBC_URL"),
    sys.env("ASSETSWEEPER_JDBC_USER"),
    sys.env("ASSETSWEEPER_PASSWORD")
  )

  lazy val vsConfig = VSConfig(
    uri"${sys.env("VIDISPINE_BASE_URL")}",
    sys.env("VIDISPINE_USER"),
    sys.env("VIDISPINE_PASSWORD")
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

  def getPathMap() = {
    val mapper = new VSPathMapper(vsConfig)
    mapper.getPathMap()
  }

  def main(args: Array[String]): Unit = {
//    val resultFuture = RunnableGraph.fromGraph(buildStream).run()
//
//    resultFuture.onComplete({
//      case Success(result)=>
//        val finalResultJson = result.asJson.toString()
//        println("Final results: ")
//        println(finalResultJson)
//        System.exit(0)
//      case Failure(err)=>
//        println(s"ERROR: ${err.toString}")
//        System.exit(1)
//    })

    getPathMap.onComplete({
      case Success(Right(pathmap))=>
        println("Got path map: ")
        println(pathmap)
        actorSystem.terminate().andThen({
          case _=>System.exit(0)
        })
      case Success(Left(err))=>
        println(s"ERROR: ${err.toString}")
        actorSystem.terminate().andThen({
          case _=>System.exit(1)
        })
      case Failure(err)=>
        logger.error("Could not map path data", err)
        actorSystem.terminate().andThen({
          case _=>System.exit(1)
        })
    })

  }
}
