import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, Broadcast, GraphDSL, Merge, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import archivehunter.ArchiveHunterLookup
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import config.ESConfig
import helpers.{CleanoutFunctions, ZonedDateTimeEncoder}
import models.{ArchiveNearlineEntry, ArchiveNearlineEntryIndexer, JobHistory, JobHistoryDAO, JobType, VSFileIndexer}
import org.slf4j.LoggerFactory
import vidispine.{VSFile, VSFileStateEncoder}
import io.circe.syntax._
import io.circe.generic.auto._
import com.sksamuel.elastic4s.circe._
import streamComponents.PeriodicUpdateBasic

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

object Main extends ZonedDateTimeEncoder with CleanoutFunctions with VSFileStateEncoder{
  val logger = LoggerFactory.getLogger(getClass)

  private implicit val actorSystem = ActorSystem("CronScanner")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)

  lazy val esConfig = ESConfig(
    sys.env.get("ES_URI"),
    sys.env.getOrElse("ES_HOST","mediacensus-elasticsearch"),
    sys.env.getOrElse("ES_PORT","9200").toInt
  )

  lazy val ahBaseUri = sys.env("ARCHIVE_HUNTER_URL")
  lazy val ahSecret = sys.env("ARCHIVE_HUNTER_SECRET")


  lazy val indexName = sys.env.getOrElse("INDEX_NAME","mediacensus-nearline")
  lazy val archiveIndexName = sys.env.getOrElse("ARCHIVER_INDEX_NAME", "mediacensus-archived-nearline")
  lazy val jobIndexName = sys.env.getOrElse("JOBS_INDEX","mediacensus-jobs")
  lazy val parallelism = sys.env.getOrElse("PARALELLISM","3").toInt

  lazy implicit val indexer = new VSFileIndexer(indexName, batchSize = 200)
  lazy implicit val archiveIndexer = new ArchiveNearlineEntryIndexer(archiveIndexName, batchSize = 200)

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
          case Right(newVersion)=>logger.info(s"Update run $updatedJobHistory with new version $newVersion")
        })
      case None => Future( () )
    }

    updateFuture.andThen({
      case _=>actorSystem.terminate().andThen({
        case _ => System.exit(exitCode)
      })
    })
  }


  def buildStream(initialJobRecord: JobHistory)(implicit esClient:ElasticClient, jobHistoryDAO: JobHistoryDAO) = {
    val counterSinkFac = Sink.fold[Int, ArchiveNearlineEntry](0)((acc,_)=>acc+1)

    GraphDSL.create(counterSinkFac) { implicit builder=> counterSink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      import com.sksamuel.elastic4s.http.ElasticDsl._

      val src = builder.add(indexer.getSource(esClient,Seq(prefixQuery("uri","omms")), limit=None))
      val lookupFactory = new ArchiveHunterLookup(ahBaseUri, ahSecret)

      val writeSink = builder.add(archiveIndexer.getSink(esClient))

      val distributor = builder.add(Balance[ArchiveNearlineEntry](parallelism))
      val distMerge = builder.add(Merge[ArchiveNearlineEntry](parallelism))

      val updater = builder.add(new PeriodicUpdateBasic[ArchiveNearlineEntry](initialJobRecord, updateEvery=1000))
      val splitter = builder.add(Broadcast[ArchiveNearlineEntry](2))

      src.out.map(_.to[VSFile]).map(ArchiveNearlineEntry.fromVSFileBlankArchivehunter) ~> distributor

      for(i <- 0 until parallelism) {
        val lookup = builder.add(lookupFactory)
        distributor.out(i) ~> lookup ~> distMerge.in(i)
      }

      distMerge ~> updater ~> splitter
      splitter.out(0) ~> writeSink
      splitter.out(1) ~> counterSink

      ClosedShape
    }
  }

  def getEsClient = Try {
    val uri = esConfig.uri match {
      case Some(uri)=>uri
      case None=>s"http://${esConfig.host}:${esConfig.port}"
    }

    ElasticClient(ElasticProperties(uri))
  }

  def main(args: Array[String]): Unit = {
    getEsClient match{
      case Failure(err)=>
        logger.error("Could not set up ES client: ", err)
        complete_run(1,None,None)(null)
        throw err
      case Success(esClient)=>
        implicit val esClientImpl = esClient
        lazy implicit val jobHistoryDAO = new JobHistoryDAO(esClient, jobIndexName)

        val runInfo = JobHistory.newRun(JobType.ArchiveHunterScan)

        val resultFuture = jobHistoryDAO.put(runInfo).flatMap(_=>{
          logger.info(s"Saved run info ${runInfo.toString}")
          RunnableGraph.fromGraph(buildStream(runInfo)).run()
        })

        resultFuture.onComplete({
          case Failure(err)=>
            logger.error("Could not run stream: ", err)
            complete_run(1,Some(err.toString),Some(runInfo))
          case Success(recordCount)=>
            logger.info(s"Counted $recordCount records, finishing")
            val updatedRunInfo = runInfo.copy(itemsCounted = recordCount)
            complete_run(0,None,Some(updatedRunInfo))
        })
    }
  }
}
