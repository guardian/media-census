import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{Broadcast, GraphDSL, Merge, RunnableGraph, Sink}
import com.sksamuel.elastic4s.ElasticsearchClientUri
import models.{AssetSweeperFile, JobHistory, JobHistoryDAO, JobType, MediaCensusEntry, MediaCensusIndexer}
import streamComponents._
import play.api.{Configuration, Logger}
import config.{DatabaseConfiguration, ESConfig, VSConfig}
import io.circe.syntax._
import io.circe.generic.auto._
import vidispineclient.VSPathMapper
import com.softwaremill.sttp._
import helpers.ZonedDateTimeEncoder
import vidispine.{VSCommunicator, VSStorage}

import scala.util.{Failure, Success, Try}
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
    sys.env.get("ES_URI"),
    sys.env.getOrElse("ES_HOST","mediacensus-elasticsearch"),
    sys.env.getOrElse("ES_PORT","9200").toInt
  )

  lazy implicit val vsCommunicator = new VSCommunicator(vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass)

  lazy val limit = sys.env.get("LIMIT").map(_.toInt)

  lazy val indexName = sys.env.getOrElse("INDEX_NAME","mediacensus")
  lazy val jobIndexName = sys.env.getOrElse("JOBS_INDEX","mediacensus-jobs")
  lazy implicit val indexer = new MediaCensusIndexer(indexName)

  /**
    * builds the main stream for conducting the mediacensus
    * @param vsPathMap Vidispine path map. Get this from the getPathMap call
    * @return
    */
  def buildStream(vsPathMap:Map[String,VSStorage], maybeStartAt:Option[Long], initialJobRecord:JobHistory)(implicit esClient:ElasticClient, jobHistoryDAO:JobHistoryDAO, indexer:MediaCensusIndexer) = {
    //val sink = Sink.seq[MediaCensusEntry]

    val counterSink = Sink.fold[Int, MediaCensusEntry](0)((acc,elem)=>acc+1)

    GraphDSL.create(counterSink) { implicit builder=> { reduceSink =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val indexSink = builder.add(indexer.getIndexSink(esClient))
      val srcFactory = new AssetSweeperFilesSource(assetSweeperConfig, maybeStartAt, limit.map(_.toLong))
      val streamSource = builder.add(srcFactory)
      val ignoresFilter = builder.add(new FilterOutIgnores)

      val knownStorageSwitch = builder.add(new KnownStorageSwitch(vsPathMap))
      val vsFileSwitch = builder.add(new VSFileSwitch())  //VSFileSwitch args are implicits
      val vsFindReplicas = builder.add(new VSFindReplicas())

      val periodicUpdate = builder.add(new PeriodicUpdate(initialJobRecord, updateEvery=500))

      val merge = builder.add(Merge[MediaCensusEntry](4,eagerComplete = false))
      val sinkBranch = builder.add(Broadcast[MediaCensusEntry](2,eagerCancel=true))
      streamSource ~> ignoresFilter ~> knownStorageSwitch

      knownStorageSwitch.out(0) ~> vsFileSwitch
      knownStorageSwitch.out(1) ~> merge

      vsFileSwitch.out(0) ~> vsFindReplicas
      vsFileSwitch.out(1) ~> merge

      vsFindReplicas.out(0) ~> merge
      vsFindReplicas.out(1) ~> merge

      merge ~> periodicUpdate ~> sinkBranch

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
        throw err //won't actually reach this line, but need an exception or return type
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

  /**
    * checks the records for the previous run; if it terminated abnormally then pick up from (roughly) where it left off.
    * @param esClient
    * @param jobHistoryDAO
    * @return
    */
  def shouldContinueFrom(esClient:ElasticClient)(implicit jobHistoryDAO: JobHistoryDAO):Future[Option[Long]] = {
    jobHistoryDAO.mostRecentJob(None).flatMap({
      case Left(err)=>
        logger.error(s"Could not look up most recent job: $err. Retrying in 5s.")
        Thread.sleep(5000)
        shouldContinueFrom(esClient)
      case Right(Some(historyItem))=>
        if(historyItem.scanFinish.isEmpty){
          logger.info(s"Last scan has no finish time, assuming it crashed. Restarting from ${historyItem.itemsCounted}")
          Future(Some(historyItem.itemsCounted))
        } else if(historyItem.lastError.isDefined){
          logger.info(s"Last scan terminated with an error: ${historyItem.lastError.get}. Restarting from ${historyItem.itemsCounted}")
          Future(Some(historyItem.itemsCounted))
        } else {
          logger.info(s"Last scan terminated normally at ${historyItem.scanFinish}. Restarting from the start")
          Future(None)
        }
      case Right(None)=>
        logger.info(s"Could not find any record of a previous run. Running from the start.")
        Future(None)
    })
  }

  def main(args: Array[String]): Unit = {
    val pathMapFuture = getPathMap()

    implicit val esClient = getEsClientWithRetry()

    try {
      checkIndex(esClient)
    } catch {
      case err:Throwable=>
        logger.error(s"Could not establish contact with Elasticsearch: ", err)
        complete_run(1,None,None)(null)
    }

    lazy implicit val jobHistoryDAO = new JobHistoryDAO(esClient, jobIndexName)

    val runInfo = JobHistory.newRun(JobType.CensusScan)

    val resultFuture = shouldContinueFrom(esClient).flatMap(maybeStartAt=>
      jobHistoryDAO.put(runInfo).flatMap(_=>{
        logger.info(s"Saved run info ${runInfo.toString}")
        pathMapFuture.flatMap({
          case Right(pathMap)=>
            RunnableGraph.fromGraph(buildStream(pathMap, maybeStartAt, runInfo)).run().map(resultCount=>Right(resultCount))
          case Left(err)=>
            Future(Left(err))
        })
      })
    )

    resultFuture.onComplete({
      case Success(Right(resultCount))=>
        println(s"Indexed a total of $resultCount items with a limit of $limit")

        indexer.calculateStats(esClient, runInfo).onComplete({
          case Failure(err)=>
            logger.error(s"Calculate stats crashed: ", err)
            complete_run(1,Some(s"Calculate stats crashed: ${err.toString}"),Some(runInfo))
          case Success(Left(errs))=>
            complete_run(1,Some(errs.mkString("; ")),Some(runInfo))
          case Success(Right(updatedJH))=>
            complete_run(0,None,Some(updatedJH))
        })

      case Success(Left(err))=>
        logger.error(s"ERROR: ${err.toString}")
        complete_run(1,Some(err.toString),Some(runInfo))
      case Failure(err)=>
        logger.error(s"Stream failed: ", err)
        complete_run(1,Some(err.toString), Some(runInfo))
    })
  }
}
