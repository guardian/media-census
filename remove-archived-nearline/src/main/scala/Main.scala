import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{Broadcast, GraphDSL, Merge, RunnableGraph, Sink}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Builder, AmazonS3ClientBuilder}
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import config.{ESConfig, VSConfig}
import helpers.{CleanoutFunctions, TrustStoreHelper, ZonedDateTimeEncoder}
import models.{ArchiveNearlineEntryIndexer, ArchivedItemRecord, JobHistory, JobHistoryDAO, JobType, VSFileIndexer}
import org.slf4j.LoggerFactory
import streamComponents.{DecodeArchiveHunterId, FixVidispineMeta, GetArchivalMetadata, LookupS3Metadata, PostLookupMerge, VSGetItem, VerifyS3Metadata}
import com.softwaremill.sttp._
import io.circe.generic.auto._
import vidispine.{ArchivalMetadata, VSCommunicator, VSFile, VSFileStateEncoder}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

object Main extends ZonedDateTimeEncoder with VSFileStateEncoder with CleanoutFunctions {
  val logger = LoggerFactory.getLogger(getClass)

  private implicit val actorSystem = ActorSystem("CronScanner")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)

  lazy val esConfig = ESConfig(
    sys.env.get("ES_URI"),
    sys.env.getOrElse("ES_HOST","mediacensus-elasticsearch"),
    sys.env.getOrElse("ES_PORT","9200").toInt
  )

  lazy val vsConfig = VSConfig(
    uri"${sys.env("VIDISPINE_BASE_URL")}",
    sys.env("VIDISPINE_USER"),
    sys.env("VIDISPINE_PASSWORD")
  )

  lazy val indexName = sys.env.getOrElse("INDEX_NAME","mediacensus-nearline")
  lazy val archiveIndexName = sys.env.getOrElse("ARCHIVER_INDEX_NAME", "mediacensus-archived-nearline")
  lazy val jobIndexName = sys.env.getOrElse("JOBS_INDEX","mediacensus-jobs")
  lazy val leaveOpenDays = sys.env.getOrElse("LEAVE_OPEN_DAYS","5").toInt

  lazy val reallyDeleteItems = sys.env.get("REALLY_DELETE") match {
    case None=>false
    case Some("true")=>true
    case Some("yes")=>true
    case Some("TRUE")=>true
    case Some("YES")=>true
    case _=>false
  }

  lazy val extraKeyStores = sys.env.get("EXTRA_KEY_STORES").map(_.split("\\s*,\\s*"))
  lazy implicit val nearlineEntriesIndexer = new VSFileIndexer(indexName, batchSize = 100)
  lazy implicit val archiveIndexer = new ArchiveNearlineEntryIndexer(archiveIndexName, batchSize = 100)

  def testGraph(esClient:ElasticClient,s3Client:AmazonS3,reallyDelete:Boolean)(implicit comm:VSCommunicator) = {
    val testSink = Sink.seq[ArchivedItemRecord]

    GraphDSL.create(testSink) {implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      import com.sksamuel.elastic4s.http.ElasticDsl._
      import com.sksamuel.elastic4s.circe._

      val src = builder.add(nearlineEntriesIndexer.getSource(esClient, Seq(existsQuery("archiveHunterId")), limit = None))
      val decoder = builder.add(new DecodeArchiveHunterId)
      val metaLookup = builder.add(new LookupS3Metadata(s3Client))
      val metaVerify = builder.add(new VerifyS3Metadata)
      val writeUpdateSink = builder.add(nearlineEntriesIndexer.getSink(esClient))

      val lookupSplitter = builder.add(Broadcast[ArchivedItemRecord](2))
      val postLookupMerge = builder.add(new PostLookupMerge)

      val vsLookup = builder.add(new VSGetItem(ArchivalMetadata.interestingFields))
      val getArchivalMeta = builder.add(new GetArchivalMetadata())

      src.out.map(_.to[VSFile]) ~> decoder
      decoder.out.map(tuple => ArchivedItemRecord(tuple._1, tuple._2, tuple._3, None)) ~> metaLookup ~> metaVerify
      metaVerify.out(0) ~> lookupSplitter
      metaVerify.out(1).map(_.nearlineItem) ~> writeUpdateSink // "NO" branch - metadata did not verify against S3, write updated item back to index

      lookupSplitter.out(0).map(_.nearlineItem) ~> vsLookup
      vsLookup.out.map(_._2) ~> getArchivalMeta ~> postLookupMerge.in0
      lookupSplitter.out(1) ~> postLookupMerge.in1

      postLookupMerge.out ~> sink
      ClosedShape
    }
  }

  def createGraph(esClient:ElasticClient,s3Client:AmazonS3,reallyDelete:Boolean)(implicit comm:VSCommunicator) = {
    val counterSinkFact = Sink.fold[Int, VSFile](0)((acc,_)=>acc+1)

    GraphDSL.create(counterSinkFact) { implicit builder=> counterSink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      import com.sksamuel.elastic4s.http.ElasticDsl._
      import com.sksamuel.elastic4s.circe._

      val src = builder.add(nearlineEntriesIndexer.getSource(esClient, Seq(existsQuery("archiveHunterId")), limit = None))
      val decoder = builder.add(new DecodeArchiveHunterId)
      val metaLookup = builder.add(new LookupS3Metadata(s3Client))
      val metaVerify = builder.add(new VerifyS3Metadata)

      val writeUpdateSink = builder.add(nearlineEntriesIndexer.getSink(esClient))

      val vsLookup = builder.add(new VSGetItem(ArchivalMetadata.interestingFields))
      val getArchivalMeta = builder.add(new GetArchivalMetadata())

      val lookupSplitter = builder.add(Broadcast[ArchivedItemRecord](2))
      val postLookupMerge = builder.add(new PostLookupMerge)

      val fixVidispineMeta = builder.add(new FixVidispineMeta())

      val deleteFile = builder.add(new VSDeleteFile(reallyDelete))
      val deleteIndexRecord = nearlineEntriesIndexer.deleteSink(esClient,reallyDelete)

      val deleteSplitter = builder.add(Broadcast[VSFile](2))

      src.out.map(_.to[VSFile]) ~> decoder
      decoder.out.map(tuple => ArchivedItemRecord(tuple._1, tuple._2, tuple._3, None)) ~> metaLookup ~> metaVerify
      metaVerify.out(1).map(_.nearlineItem) ~> writeUpdateSink // "NO" branch - metadata did not verify against S3, write updated item back to index

      metaVerify.out(0) ~> lookupSplitter
      lookupSplitter.out(0).map(_.nearlineItem) ~> vsLookup
      vsLookup.out.map(_._2) ~> getArchivalMeta ~> postLookupMerge.in0
      lookupSplitter.out(1) ~> postLookupMerge.in1

      postLookupMerge.out ~> fixVidispineMeta
      fixVidispineMeta.out.map(_.nearlineItem) ~> deleteFile ~> deleteSplitter
      deleteSplitter.out(0) ~> deleteIndexRecord
      deleteSplitter.out(1) ~> counterSink
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

  def getS3Client = Try {
    AmazonS3ClientBuilder.standard().build()
  }

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


  def main(args:Array[String]) = {
    logger.info("Starting up...")

    if(extraKeyStores.isDefined){
      logger.info(s"Loading in extra keystores from ${extraKeyStores.get.mkString(",")}")
      /* this should set the default SSL context to use the stores as well */
      TrustStoreHelper.setupTS(extraKeyStores.get) match {
        case Success(sslContext) =>
          val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
          Http().setDefaultClientHttpsContext(https)
        case Failure(err) =>
          logger.error(s"Could not set up https certs: ", err)
          System.exit(1)
      }
    }

    val connections = Seq(getEsClient, getS3Client)

    val failures = connections.collect({case Failure(err)=>err})
    if(failures.nonEmpty){
      failures.foreach(err=>
        logger.error(s"Could not establish initial connections: ", err)
      )
      System.exit(1)
    }

    val esClient = connections(0).get.asInstanceOf[ElasticClient]
    val s3Client = connections(1).get.asInstanceOf[AmazonS3]
    lazy implicit val vsCommunicator = new VSCommunicator(vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass)

    logger.info(s"ReallyDelete is set to $reallyDeleteItems")

    lazy implicit val jobHistoryDAO = new JobHistoryDAO(esClient, jobIndexName)

    Await.ready(cleanoutOldJobs(jobHistoryDAO, JobType.RemoveArchivedNearline,leaveOpenDays).map({
      case Left(errs)=>
        logger.error(s"Cleanout of old census jobs failed: $errs")
      case Right(results)=>
        logger.info(s"Cleanout of old census jobs succeeded: $results")
    }), 5 minutes)

    val runInfo = JobHistory.newRun(JobType.RemoveArchivedNearline)

    Await.ready(jobHistoryDAO.put(runInfo), 2 minutes)

    val graph = createGraph(esClient, s3Client, reallyDeleteItems)

    RunnableGraph.fromGraph(graph).run().onComplete({
      case Success(processedItemsCount)=>
        logger.info(s"Run completed, deleted $processedItemsCount items")
        Await.ready(complete_run(0,None,Some(runInfo)), 10 minutes)
      case Failure(err)=>
        logger.error(s"Run failed: ", err)
        Await.ready(complete_run(1,Some(err.getMessage),Some(runInfo)), 10 minutes)
    })
  }
}
