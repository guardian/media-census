package controllers

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import helpers.{ESClientManager, ZonedDateTimeEncoder}
import javax.inject.Inject
import models.{ArchiveNearlineEntry, MediaStatusValue, MembershipAggregationData, UnclogOutputIndexer, VSFileIndexer}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import io.circe.generic.auto._
import io.circe.syntax._
import responses.{GenericResponse, ObjectListResponse}
import vidispine.VSFileStateEncoder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NearlineDataController @Inject() (config:Configuration, cc:ControllerComponents, esClientMgr:ESClientManager)(implicit sys:ActorSystem)
  extends AbstractController(cc) with Circe with ZonedDateTimeEncoder with VSFileStateEncoder {
  private val logger = LoggerFactory.getLogger(getClass)

  private val esClient = esClientMgr.getCachedClient()
  private val indexName = config.get[String]("elasticsearch.nearlineIndexName")

  private val indexer = new VSFileIndexer(indexName)

  private val unclogIndexName = config.get[String]("elasticsearch.unclogIndexName")
  private val unclogIndexer = new UnclogOutputIndexer(unclogIndexName)

  def currentStateData = Action.async {
    indexer.aggregateByStateAndStorage(esClient).map({
      case Left(err)=>
        logger.error(s"Could not get aggregate data: $err")
        InternalServerError(GenericResponse("error",err.toString).asJson)
      case Right(result)=>
        Ok(result.asJson)
    })
  }

  def membershipStatsData = Action.async {
    Future.sequence(Seq(indexer.aggregateByMembership(esClient),indexer.totalCount(esClient))).map(results=>{
      val failures = results.collect({case Left(err)=>err})
      if(failures.nonEmpty){
        failures.foreach(err=>logger.error(err))
        InternalServerError(GenericResponse("error_list", failures.mkString(";")).asJson)
      } else {
        val successes = results.collect({case Right(result)=>result})
        val aggregateResult = successes.head.asInstanceOf[MembershipAggregationData]
        val totalCount = successes(1).asInstanceOf[Long]
        Ok(aggregateResult.copy(totalCount=totalCount).asJson)
      }
    })
  }

  def fileSearch(start:Option[String],duration:Option[Int],limit:Option[Int],orphanOnly:Boolean) = Action.async {
    val maybeStartTime = start.map(ZonedDateTime.parse)
    val maybeEndTime = start.map(ZonedDateTime.parse).flatMap(startTime=>duration.map(startTime.plusSeconds(_)))

    indexer.getResults(esClient,maybeStartTime,maybeEndTime,limit,orphanOnly).map({
      case Left(err)=>
        logger.error(err)
        InternalServerError(GenericResponse("error", err.toString).asJson)
      case Right((result, totalCount))=>
        Ok(ObjectListResponse("ok","VSFile", result, totalCount.toInt).asJson)
    })
  }

  def archivedStatsData = Action.async {
    indexer.isArchivedStats(esClient).map({
      case Left(err)=>
        logger.error(err)
        InternalServerError(GenericResponse("error", err.toString).asJson)
      case Right(stats)=>
        Ok(stats.asJson)
    })
  }

  def byCloggingStatus(status:Option[String]) = Action {
    if(status.isEmpty) {
      BadRequest(GenericResponse("missing_args","you must specify the 'status' argument").asJson)
    } else {
      val realStatus = MediaStatusValue.withName(status.get)
      val src = Source.fromGraph(
        unclogIndexer.NearlineEntriesForStatus(realStatus, esClient, indexer)
          .map(vsFile=>ByteString(vsFile.asJson.noSpaces + "\n"))
      )

      Ok.chunked(src)
    }
  }
}
