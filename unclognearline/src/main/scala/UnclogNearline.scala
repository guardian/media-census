import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{Broadcast, GraphDSL, Merge, RunnableGraph, Sink}
import com.sksamuel.elastic4s.http.ElasticDsl.matchQuery
import models._
import streamComponents._
import config.{DatabaseConfiguration, ESConfig, VSConfig}
import helpers.{CleanoutFunctions, ZonedDateTimeEncoder}

import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticError, ElasticProperties, HttpClient}
import io.circe.generic.auto._
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSFile, VSFileStateEncoder}
import com.softwaremill.sttp._

import scala.concurrent.duration._
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import com.sksamuel.elastic4s.http.search.SearchHit



object UnclogNearline extends ZonedDateTimeEncoder with VSFileStateEncoder with CleanoutFunctions {
  val logger = LoggerFactory.getLogger(getClass)

  private implicit val actorSystem = ActorSystem("CronScanner")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)

  lazy val vsConfig = VSConfig(
    uri"${sys.env("VIDISPINE_BASE_URL")}",
    sys.env("VIDISPINE_USER"),
    sys.env("VIDISPINE_PASSWORD")
  )

  lazy implicit val vsCommunicator = new VSCommunicator(vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass)

  lazy val esConfig = ESConfig(
    sys.env.get("ES_URI"),
    sys.env.getOrElse("ES_HOST","mediacensus-elasticsearch"),
    sys.env.getOrElse("ES_PORT","9200").toInt
  )

  lazy val storageId = sys.env.get("STORAGE_IDENTIFIER")
  lazy val siteIdentifierLoaded = sys.env.getOrElse("SITE_IDENTIFIER","VX")
  lazy val projectIndexName = sys.env.getOrElse("PROJECT_INDEX","projects")
  lazy implicit val projectIndexer = new PlutoProjectIndexer(projectIndexName)

  lazy val jobIndexName = sys.env.getOrElse("JOBS_INDEX","mediacensus-jobs")
  lazy val indexName = sys.env.getOrElse("INDEX_NAME", "mediacensus-commproj")
  lazy val leaveOpenDays = sys.env.getOrElse("LEAVE_OPEN_DAYS","5").toInt
  lazy val commissionIndexName = sys.env.getOrElse("COMMISSION_INDEX","commissions")
  lazy implicit val commissionIndexer = new PlutoCommissionIndexer(commissionIndexName)

  lazy val fileIndexName = sys.env.getOrElse("INDEX_NAME","mediacensus-nearline")
  lazy implicit val fileIndexer = new VSFileIndexer(fileIndexName)

  val interestingFields = Seq("gnm_storage_rule_deletable","gnm_storage_rule_deep_archive","gnm_storage_rule_sensitive","__collection")
  /**
    * Builds the main stream for processing the data
    * @return
    */
  def buildStream(initialJobRecord:JobHistory)(implicit jobHistoryDAO: JobHistoryDAO, esClient:ElasticClient,
                                               mat:Materializer) = {
    val counterSink = Sink.fold[Int, VSFile](0)((acc,elem)=>acc+1)

    GraphDSL.create(counterSink) { implicit builder=> { reduceSink =>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      import com.sksamuel.elastic4s.http.ElasticDsl._
      import io.circe.generic.auto._
      import com.sksamuel.elastic4s.circe._

      val src = fileIndexer.getSource(esClient,Seq(matchQuery("storage",storageId)),limit=None)
      val lookup = builder.add(new VSGetItem(interestingFields))

      //src.map(_.to[VSFile]) ~> lookup ~>
      //src.out.map(_.to[VSFile])
      //src.map(_.to[VSFile])
      //src.map(_.to[VSFile])
      //src.map(VSFile)
      src.map(_.to[VSFile]) ~> lookup
      lookup.out.map(_._2)

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
    val checkResult = Await.result(commissionIndexer.checkIndex(esClient), 30 seconds)

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
        jobHistoryDAO.updateStatusOnly(updatedJobHistory).map({
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
    implicit val esClient = getEsClientWithRetry()

    try {
      checkIndex(esClient)
    } catch {
      case err:Throwable=>
        logger.error(s"Could not establish contact with Elasticsearch: ", err)
        complete_run(1,None,None)(null)
    }

    lazy implicit val jobHistoryDAO = new JobHistoryDAO(esClient, jobIndexName)

    Await.ready(cleanoutOldJobs(jobHistoryDAO, JobType.CommissionScan,leaveOpenDays).map({
      case Left(errs)=>
        logger.error(s"Cleanout of old census jobs failed: $errs")
      case Right(results)=>
        logger.info(s"Cleanout of old census jobs succeeded: $results")
    }), 5 minutes)

    val runInfo = JobHistory.newRun(JobType.CommissionScan)

    val resultFuture = jobHistoryDAO.put(runInfo).flatMap({
      case Right(_)=>
        logger.info(s"Saved run info ${runInfo.toString}")
        RunnableGraph.fromGraph(buildStream(runInfo)).run().map(resultCount=>Right(resultCount))
      case Left(err)=>
        Future(Left(err))
    })

    resultFuture.onComplete({
      case Success(Right(resultCounts))=>
        val commissionResultCount = resultCounts.head
        val projectResultCount = resultCounts(1)
        println(s"Found a total of $commissionResultCount commissions and $projectResultCount projects that are now indexed")
        complete_run(0,None,None)
      case Success(Left(err))=>
        logger.error(s"ERROR: ${err.toString}")
        complete_run(1,Some(err.toString),None)
      case Failure(err)=>
        logger.error(s"Stream failed: ", err)
        complete_run(1,Some(err.toString),None)
    })
  }
}