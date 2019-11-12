import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{Broadcast, GraphDSL}
import com.amazonaws.services.s3.AmazonS3
import com.sksamuel.elastic4s.http.ElasticClient
import config.ESConfig
import models.{ArchiveNearlineEntryIndexer, ArchivedItemRecord, VSFileIndexer}
import org.slf4j.LoggerFactory
import streamComponents.{DecodeArchiveHunterId, FixVidispineMeta, GetArchivalMetadata, LookupS3Metadata, PostLookupMerge, VSGetItem, VerifyS3Metadata}
import io.circe.syntax._
import io.circe.generic.auto._
import vidispine.{ArchivalMetadata, VSCommunicator, VSFile}

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends {
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

  lazy implicit val nearlineEntriesIndexer = new VSFileIndexer(indexName, batchSize = 200)
  lazy implicit val archiveIndexer = new ArchiveNearlineEntryIndexer(archiveIndexName, batchSize = 200)


  def createGraph(esClient:ElasticClient,s3Client:AmazonS3)(implicit comm:VSCommunicator) = {
    GraphDSL.create() { implicit builder=>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      import com.sksamuel.elastic4s.http.ElasticDsl._
      import com.sksamuel.elastic4s.circe._

      val src = builder.add(nearlineEntriesIndexer.getSource(esClient, Seq(existsQuery("archiveHunterId")), limit=None))
      val decoder = builder.add(new DecodeArchiveHunterId)
      val metaLookup = builder.add(new LookupS3Metadata(s3Client))
      val metaVerify = builder.add(new VerifyS3Metadata)

      val writeUpdateSink = builder.add(nearlineEntriesIndexer.getSink(esClient))

      val vsLookup = builder.add(new VSGetItem(ArchivalMetadata.interestingFields))
      val getArchivalMeta = builder.add(new GetArchivalMetadata())

      val lookupSplitter = builder.add(Broadcast[ArchivedItemRecord](2))
      val postLookupMerge = builder.add(new PostLookupMerge)

      val fixVidispineMeta = builder.add(new FixVidispineMeta())

      src.out.map(_.to[VSFile]) ~> decoder
      decoder.out.map(tuple=>ArchivedItemRecord(tuple._1,tuple._2,tuple._3,None)) ~> metaLookup ~> metaVerify
      metaVerify.out(1).map(_.nearlineItem) ~> writeUpdateSink  // "NO" branch - metadata did not verify against S3, write updated item back to index

      metaVerify.out(0) ~> lookupSplitter
      lookupSplitter.out(0).map(_.nearlineItem) ~> vsLookup
      vsLookup.out.map(_._2) ~> getArchivalMeta ~> postLookupMerge.in0
      lookupSplitter.out(1) ~> postLookupMerge.in1

      postLookupMerge.out ~> fixVidispineMeta
      ClosedShape
    }
  }
  def main(args:Array[String]) = {

  }
}
