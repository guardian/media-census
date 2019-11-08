package controllers

import helpers.ESClientManager
import javax.inject.Inject
import models.ArchiveNearlineEntryIndexer
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import responses.{GenericResponse, SimplePieResponse}
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global

class ArchivedDataController @Inject() (config:Configuration, esClientManager: ESClientManager, cc:ControllerComponents)
  extends AbstractController(cc) with Circe {

  private val logger = LoggerFactory.getLogger(getClass)

  private val indexer = new ArchiveNearlineEntryIndexer(config.getOptional[String]("index.archiveEntry").getOrElse("mediacensus-archived-nearline"))

  private val esClient = esClientManager.getCachedClient()

  def statsByCollection = Action.async {
    indexer.statsByCollection(esClient).map({
      case Left(err)=>
        logger.error(s"Could not get stats by collection: ${err.toString}")
        InternalServerError(GenericResponse("db_error", err.toString).asJson)
      case Right(results)=>
        logger.info(s"Got stats: ${results.data}")
        SimplePieResponse.fromAggregations(results, "byCollection", Some("noCollection"), "ok") match {
          case Right(pieResponse)=>
            Ok(pieResponse.asJson)
          case Left(err)=>
            logger.error(s"Could not compile data response: $err")
            InternalServerError(GenericResponse("error", err).asJson)
        }

    })
  }

  def statsByVSStorage = Action.async {
    indexer.statsByVSStorage(esClient).map({
      case Left(err)=>
        logger.error(s"Could not get stats by VS storage: ${err.toString}")
        InternalServerError(GenericResponse("db_error", err.toString).asJson)
      case Right(results)=>
        logger.info(s"Got stats: ${results.data}")
        Ok(GenericResponse("ok","got data, rendering not implemented").asJson)
    })
  }
}
