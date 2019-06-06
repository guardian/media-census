import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{Broadcast, GraphDSL, Merge, RunnableGraph, Sink}
import com.sksamuel.elastic4s.ElasticsearchClientUri
import models.{AssetSweeperFile, JobHistory, JobHistoryDAO, MediaCensusEntry, MediaCensusIndexer}
import streamComponents._
import play.api.{Configuration, Logger}
import config.{DatabaseConfiguration, ESConfig, VSConfig}
import io.circe.syntax._
import io.circe.generic.auto._
import com.softwaremill.sttp._
import helpers.ZonedDateTimeEncoder
import vidispine.{VSCommunicator, VSStorage}

import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticError, ElasticProperties, HttpClient}

import scala.concurrent.duration._

object DeleteScanner extends ZonedDateTimeEncoder {
  val logger = Logger(getClass)

  private implicit val actorSystem = ActorSystem("CronScanner")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)

  lazy val assetSweeperConfig = DatabaseConfiguration(
    sys.env.getOrElse("ASSETSWEEPER_JDBC_DRIVER","org.postgresql.Driver"),
    sys.env("ASSETSWEEPER_JDBC_URL"),
    sys.env("ASSETSWEEPER_JDBC_USER"),
    sys.env("ASSETSWEEPER_PASSWORD")
  )

  lazy val esConfig = ESConfig(
    sys.env.get("ES_URI"),
    sys.env.getOrElse("ES_HOST","mediacensus-elasticsearch"),
    sys.env.getOrElse("ES_PORT","9200").toInt
  )

  lazy val indexName = sys.env.getOrElse("INDEX_NAME","mediacensus")
  lazy val jobIndexName = sys.env.getOrElse("JOBS_INDEX","mediacensus-jobs")
  lazy val indexer = new MediaCensusIndexer(indexName)

  /**
    * builds the main stream for conducting the delete scan
    * @return
    */
  def buildStream(esClient:ElasticClient) = {
    val counterSink = Sink.fold[Int, AssetSweeperFile](0)((acc,elem)=>acc+1)

    GraphDSL.create(counterSink) { implicit builder=> { reduceSink =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val deleteSink = builder.add(indexer.getDeleteSink(esClient))
      val srcFactory = new AssetSweeperDeletedSource(assetSweeperConfig)
      val streamSource = builder.add(srcFactory)
      val sinkSplitter = builder.add(Broadcast[AssetSweeperFile](2, eagerCancel=false))

      streamSource.out.log("deleteStream") ~> sinkSplitter
      sinkSplitter ~> deleteSink
      sinkSplitter ~> reduceSink

      ClosedShape
    }}
  }


  def getEsClient = Try {
    val uri = esConfig.uri match {
      case Some(uri)=>uri
      case None=>s"http://${esConfig.host}:${esConfig.port}"
    }

    ElasticClient(ElasticProperties(uri))
  }

  def getEsClientWithRetry(attempt:Int=0):ElasticClient = getEsClient match {
    case Failure(err)=>
      logger.error(s"Could not connect to ES, trying again: ", err)
      Thread.sleep(3000)
      if(attempt>10) {
        logger.error(s"Failed 10 times, not trying any more")
        Await.ready(complete_run(2, None,None)(null), 60 seconds)
        throw err
      } else {
        getEsClientWithRetry(attempt+1)
      }
    case Success(client)=>client
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

  /**
    * shuts down the actor system and exit the JVM. Optionally, update and save a [[JobHistory]] to the index beforehand
    * @param exitCode exit code for the system
    * @param errorMessage optional string of an error message to show in the JobHistory
    * @param runInfo optional [[JobHistory]] to be saved and updated
    * @param jobHistoryDAO implicitly provided DAO for JobHistory
    * @return a Future
    */
  def complete_run(exitCode:Int, errorMessage:Option[String], runInfo:Option[JobHistory])(implicit jobHistoryDAO: JobHistoryDAO) = {
    val updateFuture = runInfo match {
      case Some(jobHistory)=>
        val updatedJobHistory = jobHistory.copy(scanFinish = Some(ZonedDateTime.now()), lastError = errorMessage)
        jobHistoryDAO.put(updatedJobHistory).map({
          case Left(err)=>logger.error(s"Could not update job history entry: ${err.toString}")
          case Right(newVersion)=>logger.info(s"Update run ${updatedJobHistory} with new version $newVersion")
        })
      case None => Future( () )
    }

    updateFuture.andThen({
      case _=>actorSystem.terminate().andThen({
        case _ => System.exit(exitCode)
      })
    })
  }

  def main(args: Array[String]): Unit = {

    val esClient = getEsClientWithRetry()

    try {
      checkIndex(esClient)
    } catch {
      case err:Throwable=>
        logger.error(s"Could not establish contact with Elasticsearch: ", err)
        complete_run(1,None,None)(null)
    }

    lazy implicit val jobHistoryDAO = new JobHistoryDAO(esClient, jobIndexName)

//    val resultFuture = jobHistoryDAO.put(runInfo).map({
//      case Right(_)=>
//        logger.info(s"Saved run info ${runInfo.toString}")
        val resultFuture:Future[Either[ElasticError, Int]] = RunnableGraph.fromGraph(buildStream(esClient)).run().map(resultCount=>Right(resultCount))
//      case Left(err)=>
//        Left(err)
//      })

    resultFuture.onComplete({
      case Success(Right(resultCount))=>
        println(s"Deleted a total of $resultCount items that are now moved off primary storage")
        complete_run(0,None,None)
//        indexer.calculateStats(esClient, runInfo).onComplete({
//          case Failure(err)=>
//            logger.error(s"Calculate stats crashed: ", err)
//            complete_run(1,Some(s"Calculate stats crashed: ${err.toString}"),Some(runInfo))
//          case Success(Left(errs))=>
//            complete_run(1,Some(errs.mkString("; ")),Some(runInfo))
//          case Success(Right(updatedJH))=>
//            complete_run(0,None,Some(updatedJH))
//        })

      case Success(Left(err))=>
        logger.error(s"ERROR: ${err.toString}")
        complete_run(1,Some(err.toString),None)
      case Failure(err)=>
        logger.error(s"Stream failed: ", err)
        complete_run(1,Some(err.toString),None)
    })
  }
}
