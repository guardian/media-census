package controllers

import com.sksamuel.elastic4s.http.ElasticClient
import helpers.ESClientManager
import javax.inject.Inject
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.mvc.{AbstractController, ControllerComponents}
import models.UnclogOutputIndexer
import responses.GenericResponse
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.libs.circe.Circe
import scala.concurrent.ExecutionContext.Implicits.global

class UnclogNearlineDataController @Inject() (config:Configuration, esClientMgr:ESClientManager, cc:ControllerComponents) extends AbstractController(cc) with Circe {
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
}
