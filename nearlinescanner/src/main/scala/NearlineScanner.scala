import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, GraphDSL, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import config.{DatabaseConfiguration, ESConfig, VSConfig}
import models.{JobHistory, JobHistoryDAO, JobType, MediaCensusEntry, MediaCensusIndexer, VSFileIndexer}
import play.api.Logger
import vidispine.{VSCommunicator, VSFile}
import com.softwaremill.sttp._
import streamComponents.{VSStorageScanSource,PeriodicUpdate}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object NearlineScanner {
  val logger = Logger(getClass)

  private implicit val actorSystem = ActorSystem("NearlineScanner")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)


  lazy val vsConfig = VSConfig(
    uri"${sys.env("VIDISPINE_BASE_URL")}",
    sys.env("VIDISPINE_USER"),
    sys.env("VIDISPINE_PASSWORD")
  )

  lazy val esConfig = ESConfig(
    sys.env.get("ES_URI"),
    sys.env.getOrElse("ES_HOST","mediacensus-elasticsearch"),
    sys.env.getOrElse("ES_PORT","9200").toInt
  )

  lazy implicit val vsCommunicator = new VSCommunicator(vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass)

  lazy val limit = sys.env.get("LIMIT").map(_.toInt)

  lazy val storageToScan = sys.env.get("STORAGE_ID")

  lazy val indexName = sys.env.getOrElse("INDEX_NAME","mediacensus-nearline")
  lazy val jobIndexName = sys.env.getOrElse("JOBS_INDEX","mediacensus-jobs")
  lazy val indexer = new VSFileIndexer(indexName, batchSize = 200)
  lazy implicit val mcIndexer = new MediaCensusIndexer(sys.env.getOrElse("CENSUS_INDEXNAME","mediacensus"))

  def buildStream(initialJobRecord: JobHistory, storageId:String)(implicit esClient:ElasticClient, jobHistoryDAO: JobHistoryDAO,indexer: VSFileIndexer) = {
    val counterSink = Sink.fold[Int, VSFile](0)((acc,_)=>acc+1)

    GraphDSL.create(counterSink) { implicit builder=> counter=>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      val src = builder.add(new VSStorageScanSource(Some(storageId), None, vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass,pageSize=100))
      val updater = builder.add(new PeriodicUpdate[VSFile](initialJobRecord))

      val sink = builder.add(indexer.getSink(esClient))
      val splitter = builder.add(new Broadcast[VSFile](2,eagerCancel = true))

      src.out.log("vs-file-indexer-stream") ~> splitter ~> sink
      splitter.out(1) ~> counter
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

  def main(args:Array[String]) = {
    getEsClient match{
      case Failure(err)=>
        logger.error("Could not set up ES client: ", err)
        complete_run(1,None,None)(null)
        throw err
      case Success(esClient)=>
        lazy implicit val jobHistoryDAO = new JobHistoryDAO(esClient, jobIndexName)

        val runInfo = JobHistory.newRun(JobType.NearlineScan)

        storageToScan match {
          case None=>
            logger.error(s"You must specify a storage to scan")
            complete_run(1,None,None)(null)
            throw new RuntimeException
          case Some(storageId)=>
            val resultFuture = jobHistoryDAO.put(runInfo).flatMap(_=>{
              logger.info(s"Saved run info ${runInfo.toString}")
              RunnableGraph.fromGraph(buildStream(esClient,storageId)).run()
            })

            resultFuture.onComplete({
              case Failure(err)=>
                logger.error("Could not run stream: ", err)
                complete_run(1,None,None)
              case Success(recordCount)=>
                logger.info(s"Counted $recordCount records, finishing")
                complete_run(0,None,None)
            })
        }

    }
  }

}
