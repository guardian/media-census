import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, Merge, RunnableGraph, Sink}
import models.{AssetSweeperFile, MediaCensusEntry}
import streamComponents._
import play.api.{Configuration, Logger}
import config.{DatabaseConfiguration, VSConfig}
import io.circe.syntax._
import io.circe.generic.auto._
import vidispineclient.VSPathMapper
import com.softwaremill.sttp._
import vidispine.{VSCommunicator, VSStorage}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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

  lazy implicit val vsCommunicator = new VSCommunicator(vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass)

  lazy val limit = sys.env.get("LIMIT").map(_.toInt)

  /**
    * builds the main stream for conducting the mediacensus
    * @param vsPathMap Vidispine path map. Get this from the getPathMap call
    * @return
    */
  def buildStream(vsPathMap:Map[String,VSStorage]) = {
    val sink = Sink.seq[MediaCensusEntry]

    GraphDSL.create(sink) { implicit builder=> { streamSink =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val srcFactory = new AssetSweeperFilesSource(assetSweeperConfig, limit)
      val streamSource = builder.add(srcFactory)
      val ignoresFilter = builder.add(new FilterOutIgnores)

      val knownStorageSwitch = builder.add(new KnownStorageSwitch(vsPathMap))
      val vsFileSwitch = builder.add(new VSFileSwitch())  //VSFileSwitch args are implicits

      val merge = builder.add(Merge[MediaCensusEntry](3,eagerComplete = false))
      streamSource ~> ignoresFilter ~> knownStorageSwitch

      knownStorageSwitch.out(0) ~> vsFileSwitch
      knownStorageSwitch.out(1) ~> merge

      vsFileSwitch.out(0) ~> merge
      vsFileSwitch.out(1) ~> merge

      merge ~> streamSink

      ClosedShape
    }}
  }

  /**
    * returns the vidispine path map, i.e. a Map of the on-disk root path (as a string) to the storage definition
    * for all "filesystem" type storages
    * @return a Future, containing either an error message or a Map[String, VSStorage]
    */
  def getPathMap() = {
    val mapper = new VSPathMapper(vsConfig)
    mapper.getPathMap()
  }

  def main(args: Array[String]): Unit = {
//    val resultFuture = getPathMap().flatMap({

//    }

    val pathMapFuture = getPathMap()

    val resultFuture = pathMapFuture.flatMap({
      case Right(pathMap)=>
        RunnableGraph.fromGraph(buildStream(pathMap)).run().map(results=>Right(results))
      case Left(err)=>
        Future(Left(err))
    })

    resultFuture.onComplete({
      case Success(Right(result))=>
        val finalResultJson = result.asJson.toString()
        println("Final results: ")
        println(finalResultJson)
        println(s"Got a total of ${result.length} items with a limit of $limit")
        Thread.sleep(10000)
        actorSystem.terminate().andThen({
          case _=>System.exit(0)
        })
      case Failure(err)=>
        println(s"ERROR: ${err.toString}")
        actorSystem.terminate().andThen({
          case _=>System.exit(1)
        })
    })

//    getPathMap.onComplete({
//      case Success(Right(pathmap))=>
//        println("Got path map: ")
//        println(pathmap)
//        actorSystem.terminate().andThen({
//          case _=>System.exit(0)
//        })
//      case Success(Left(err))=>
//        println(s"ERROR: ${err.toString}")
//        actorSystem.terminate().andThen({
//          case _=>System.exit(1)
//        })
//      case Failure(err)=>
//        logger.error("Could not map path data", err)
//        actorSystem.terminate().andThen({
//          case _=>System.exit(1)
//        })
//    })

  }
}
