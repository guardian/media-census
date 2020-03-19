package controllers

import com.sksamuel.elastic4s.http.ElasticClient
import helpers.ESClientManager
import javax.inject.Inject
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.mvc.{AbstractController, ControllerComponents}
import models.{MediaStatusValue, UnclogOutputIndexer}
import responses.{GenericResponse, ObjectGetResponse, ObjectListResponse}
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.libs.circe.Circe

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class UnclogNearlineDataController @Inject() (config:Configuration, esClientMgr:ESClientManager, cc:ControllerComponents) extends AbstractController(cc) with Circe {
  import models.MediaStatusValueEncoder._
  private val logger = LoggerFactory.getLogger(getClass)
  lazy val indexName = config.get[String]("elasticsearch.unclogIndexName")

  private lazy val indexer = new UnclogOutputIndexer(indexName)

  private implicit val esClient:ElasticClient = esClientMgr.getCachedClient()

  def mediaStatusStats = Action.async {
    indexer.getMediaStatusStats.map({
      case Left(err)=>
        InternalServerError(GenericResponse("db_error", err.toString).asJson)
      case Right(dataMap)=>
        Ok(dataMap.asJson)
    })
  }

  def recordsForMediaStatus(statusValueString:String, startAt:Option[Int], limit:Option[Int]) = Action.async {
    val realLimit = limit.getOrElse(100)
    val realStartAt = startAt.getOrElse(0)
    Try { MediaStatusValue.withName(statusValueString) } match {
      case Failure(err)=>
        logger.error(err.toString)
        Future(BadRequest(GenericResponse("bad_request","invalid status value").asJson))
      case Success(statusValue)=>
        indexer.recordsForMediaStatus(statusValue, realStartAt, realLimit).map({
          case Left(err)=>
            logger.error(s"Could not list out items for state ${statusValue}: ", err)
            InternalServerError(GenericResponse("db_error", err.toString).asJson)
          case Right((results, hitCount))=>
            Ok(ObjectListResponse("ok","unclog-nearline", results, hitCount.toInt).asJson)
        })
    }
  }

  def projectsForMediaStatus(statusValueString:String) = Action.async {
    Try { MediaStatusValue.withName(statusValueString) } match {
      case Failure(err)=>
        logger.error(err.toString)
        Future(BadRequest(GenericResponse("bad_request","invalid status value").asJson))
      case Success(statusValue)=>
        indexer.projectsForMediaStatus(statusValue).map({
          case Left(err)=>
            logger.error(s"Could not list out items for state ${statusValue}: ", err)
            InternalServerError(GenericResponse("db_error", err.toString).asJson)
          case Right(results)=>
            Ok(ObjectGetResponse("ok","projects-breakdown",results).asJson)
        })
    }
  }
}
