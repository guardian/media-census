import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, Merge, RunnableGraph, Sink}
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import config.ESConfig
import fomStreamComponents.ExistsInS3Switch
import helpers.ZonedDateTimeEncoder
import models.{ExistsReport, JobHistory, JobHistoryDAO, VSFileIndexer}
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

  lazy val s3Bucket = sys.env.get("S3_BUCKET_NAME") match {
    case Some(b)=>b
    case None=>
      logger.error("You must specify S3_BUCKET_NAME in the environment")
      Await.ready(complete_run(1), 60 seconds)
      ""  //this is never reached
  }

  def getEsClient = Try {
    val uri = esConfig.uri match {
      case Some(uri)=>uri
      case None=>s"http://${esConfig.host}:${esConfig.port}"
    }

    ElasticClient(ElasticProperties(uri))
  }

  def buildStream(esClient:ElasticClient) = {
    val outputSinkFactory = Sink.fold[Seq[ExistsReport],ExistsReport](Seq())((acc,item)=>acc++Seq(item))

    GraphDSL.create(outputSinkFactory) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(indexer.getOrphansSource(esClient))
      val existsSwitch = builder.add(new ExistsInS3Switch(s3Bucket))
      val merge = builder.add(Merge[ExistsReport](2))

      src.out.map(_.to[VSFile]) ~> existsSwitch

      existsSwitch.out(0).map(vsfile=>ExistsReport(vsfile,true)) ~> merge
      existsSwitch.out(1).map(vsfile=>ExistsReport(vsfile,false)) ~> merge
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
    val maybeResultFuture = getEsClient.map(esClient=>RunnableGraph.fromGraph(buildStream(esClient)).run())

    maybeResultFuture match {
      case Failure(err)=>
        logger.error("Could not get elasticsearch client: ", err)
        complete_run(1)
      case Success(resultFuture)=>resultFuture.onComplete({
        case Failure(err)=>
          logger.error("Processing stream terminated: ", err)
          complete_run(2)
        case Success(reportList)=>
          val didExistCount = reportList.count(_.existsInS3==true)
          val notExistCount = reportList.count(_.existsInS3==false)
          logger.info(s"Run completed.  $didExistCount files existed in S3 and $notExistCount files did not exist.")
      })
    }
  }
}
