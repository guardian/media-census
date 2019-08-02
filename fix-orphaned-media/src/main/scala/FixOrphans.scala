import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink}
import com.om.mxs.client.japi.UserInfo
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import config.ESConfig
import fomStreamComponents.{CopyToS3, ExistsInS3Switch, NoMembershipFilter}
import helpers.ZonedDateTimeEncoder
import models.{CopyStatus, ExistsReport, FOMCopyReport, S3Destination, VSFileIndexer}
import play.api.Logger
import vidispine.{VSFile, VSFileStateEncoder}

import scala.util.{Failure, Success, Try}
import io.circe.generic.auto._
import com.sksamuel.elastic4s.circe._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object FixOrphans extends ZonedDateTimeEncoder with VSFileStateEncoder {
  val logger = Logger(getClass)

  private implicit val actorSystem = ActorSystem("NearlineScanner")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)

  lazy val indexName = sys.env.getOrElse("INDEX_NAME","mediacensus-nearline")
  lazy val indexer = new VSFileIndexer(indexName, batchSize = 200)

  /*
    * in addition to these parameters, we expect standard AWS setup - AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION etc.
    */
  lazy val esConfig = ESConfig(
    sys.env.get("ES_URI"),
    sys.env.getOrElse("ES_HOST","mediacensus-elasticsearch"),
    sys.env.getOrElse("ES_PORT","9200").toInt
  )

  lazy val s3BucketList = sys.env.get("S3_BUCKET_LIST") match {
    case Some(b)=>b.split("\\s*,\\s*").toList
    case None=>
      logger.error("You must specify S3_BUCKET_LIST in the environment")
      Await.ready(complete_run(1), 60 seconds)
      List("")  //this is never reached
  }

  lazy val vaultFile:String = sys.env.get("VAULT_FILE") match {
    case Some(str)=>str
    case None=>
      logger.error("You must specify VAULT_FILE to point to a .vault file with login credentials for the target vault")
      Await.ready(complete_run(1), 1 hour)
      throw new RuntimeException("timed out waiting for actor system to exit")
  }

  lazy val targetBucket:String = sys.env.get("TARGET_BUCKET") match {
    case Some(str)=>str
    case None=>
      logger.error("You must specify TARGET_BUCKET to point the bucket to upload to")
      Await.ready(complete_run(1), 1 hour)
      throw new RuntimeException("timed out waiting for actor system to exit")
  }

  lazy val parallelism = sys.env.getOrElse("PARALLELISM","10").toInt

  lazy val maybeLimit = sys.env.get("LIMIT").map(_.toInt)

  lazy val forStorage = sys.env.get("STORAGE_ID") match {
    case Some(str)=>str
    case None=>
      logger.error("You must specify STORAGE_ID to point to the Vidispine storage corresponding to the objectmatrix vault in VAULT_FILE")
      Await.ready(complete_run(1), 1 hour)
      throw new RuntimeException("timed out waiting for actor system to exit")
  }

  def getEsClient = Try {
    val uri = esConfig.uri match {
      case Some(uri)=>uri
      case None=>s"http://${esConfig.host}:${esConfig.port}"
    }

    ElasticClient(ElasticProperties(uri))
  }

  def buildStream(esClient:ElasticClient, userInfo:UserInfo) = {
    logger.info(s"Parallelism is $parallelism, limit is $maybeLimit")
    val outputSinkFactory = Sink.fold[Seq[FOMCopyReport],FOMCopyReport](Seq())((acc,item)=>acc++Seq(item))

    GraphDSL.create(outputSinkFactory) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(indexer.getOrphansSource(esClient, Some(forStorage), maybeLimit))
      val existsSwitchFactory = new ExistsInS3Switch(s3BucketList).async
      val noMembershipSwitchFactory = new NoMembershipFilter()
      val copyToS3 = builder.add(new CopyToS3(userInfo, targetBucket))
      val splitter = builder.add(Balance[VSFile](parallelism))
      val merge = builder.add(Merge[FOMCopyReport](2*parallelism))

      src.out.map(_.to[VSFile]) ~> splitter

      for (i<-0 until parallelism) {
        val existsSwitch = builder.add(existsSwitchFactory)
        val noMembershipFilter = builder.add(noMembershipSwitchFactory)
        splitter.out(i) ~> noMembershipFilter ~> existsSwitch
        existsSwitch.out(0).map(vsfile=>FOMCopyReport(vsfile,S3Destination(targetBucket,vsfile.path),CopyStatus.EXISTS_ALREADY)) ~> merge //YES, the file exists in S3
        existsSwitch.out(1) ~> copyToS3 ~> merge  //NO, the file does not exist in S3
      }

      merge ~> sink
      ClosedShape
    }
  }

  def complete_run(exitCode:Int) = {
      actorSystem.terminate().andThen({
        case _ => System.exit(exitCode)
      })
  }

  def main(args:Array[String]):Unit = {
    val userInfo = UserInfoBuilder.fromFile(vaultFile)

    val maybeResultFuture = userInfo.flatMap(realUserInfo=>getEsClient.map(esClient=>RunnableGraph.fromGraph(buildStream(esClient,realUserInfo)).run()))

    maybeResultFuture match {
      case Failure(err)=>
        logger.error("Could not get elasticsearch client: ", err)
        complete_run(1)
      case Success(resultFuture)=>resultFuture.onComplete({
        case Failure(err)=>
          logger.error("Processing stream terminated: ", err)
          complete_run(2)
        case Success(reportList)=>
          val didExistCount = reportList.count(_.status==CopyStatus.EXISTS_ALREADY)
          val didExistSize = reportList.filter(_.status==CopyStatus.EXISTS_ALREADY).foldLeft(0L)((totalSize,elem)=>totalSize+elem.source.size) / 1024^3
          val notExistCount = reportList.count(_.status==CopyStatus.SOURCE_NOT_FOUND)
          val notExistSize = reportList.filter(_.status==CopyStatus.SOURCE_NOT_FOUND).foldLeft(0L)((totalSize,elem)=>totalSize+elem.source.size) / 1024^3
          logger.info(s"Run completed.  $didExistCount ($didExistSize Gb) files existed in S3 and $notExistCount ($notExistSize Gb) files not found.")
          complete_run(0)
      })
    }
  }
}
