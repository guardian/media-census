import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{Broadcast, GraphDSL, Merge, RunnableGraph, Sink}
import com.sksamuel.elastic4s.ElasticsearchClientUri
import models.{AssetSweeperFile, JobHistory, JobHistoryDAO, MediaCensusEntry}
import streamComponents._
import play.api.{Configuration, Logger}
import config.{DatabaseConfiguration, ESConfig, VSConfig}
import io.circe.syntax._
import io.circe.generic.auto._
import vidispineclient.VSPathMapper
import com.softwaremill.sttp._
import helpers.ZonedDateTimeEncoder
import vidispine.{VSCommunicator, VSStorage}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties, HttpClient}

import scala.concurrent.duration._

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

  lazy val esConfig = ESConfig(
    None,
    sys.env.getOrElse("ES_HOST","mediacensus-elasticsearch"),
    sys.env.getOrElse("ES_PORT","9200").toInt
  )

  lazy implicit val vsCommunicator = new VSCommunicator(vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass)

  lazy val limit = sys.env.get("LIMIT").map(_.toInt)

  lazy val indexName = sys.env.getOrElse("INDEX_NAME","mediacensus")
  lazy val jobIndexName = sys.env.getOrElse("JOBS_INDEX","mediacensus-jobs")
  lazy val indexer = new Indexer(indexName)

  /**
    * builds the main stream for conducting the mediacensus
    * @param vsPathMap Vidispine path map. Get this from the getPathMap call
    * @return
    */
  def buildStream(vsPathMap:Map[String,VSStorage], esClient:ElasticClient) = {
    //val sink = Sink.seq[MediaCensusEntry]


    val counterSink = Sink.fold[Int, MediaCensusEntry](0)((acc,elem)=>acc+1)

    GraphDSL.create(counterSink) { implicit builder=> { reduceSink =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val indexSink = builder.add(indexer.getIndexSink(esClient))
      val srcFactory = new AssetSweeperFilesSource(assetSweeperConfig, limit)
      val streamSource = builder.add(srcFactory)
      val ignoresFilter = builder.add(new FilterOutIgnores)

      val knownStorageSwitch = builder.add(new KnownStorageSwitch(vsPathMap))
      val vsFileSwitch = builder.add(new VSFileSwitch())  //VSFileSwitch args are implicits
      val vsFindReplicas = builder.add(new VSFindReplicas())

      val merge = builder.add(Merge[MediaCensusEntry](4,eagerComplete = false))
      val sinkBranch = builder.add(Broadcast[MediaCensusEntry](2,eagerCancel=true))
      streamSource ~> ignoresFilter ~> knownStorageSwitch

      knownStorageSwitch.out(0) ~> vsFileSwitch
      knownStorageSwitch.out(1) ~> merge

      vsFileSwitch.out(0) ~> vsFindReplicas
      vsFileSwitch.out(1) ~> merge

      vsFindReplicas.out(0) ~> merge
      vsFindReplicas.out(1) ~> merge

      merge ~> sinkBranch

      sinkBranch.out(0) ~> indexSink
      sinkBranch.out(1) ~> reduceSink
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

  def getEsClient = {
    val uri = esConfig.uri match {
      case Some(uri)=>uri
      case None=>s"http://${esConfig.host}:${esConfig.port}"
    }

    ElasticClient(ElasticProperties(uri))
  }

  def checkIndex(esClient:ElasticClient):Unit = {
    //we can't continue startup until this is done anyway, so it's fine to block here.
    val checkResult = Await.result(indexer.checkIndex(esClient), 30 seconds)

    if(checkResult.isError) {
      logger.error(s"Could not check index status: ${checkResult.error}")
      actorSystem.terminate().andThen({
        case _ => System.exit(1)
      })
    } else {
      logger.info(s"Successfully connected to index $indexName on ${esConfig.host}:${esConfig.port}")
    }
  }

  def main(args: Array[String]): Unit = {
    val pathMapFuture = getPathMap()

    val esClient = getEsClient

    checkIndex(esClient)

    lazy val jobHistoryDAO = new JobHistoryDAO(esClient, jobIndexName)

    val runInfo = JobHistory.newRun()

    jobHistoryDAO.put(runInfo)

    val resultFuture = pathMapFuture.flatMap({
      case Right(pathMap)=>
        RunnableGraph.fromGraph(buildStream(pathMap, esClient)).run().map(resultCount=>Right(resultCount))
      case Left(err)=>
        Future(Left(err))
    })

    resultFuture.onComplete({
      case Success(Right(resultCount))=>
        println(s"Indexed a total of $resultCount items with a limit of $limit")

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
