package controllers

import helpers.{ESClientManager, ZonedDateTimeEncoder}
import javax.inject.{Inject, Singleton}
import models.{MediaCensusEntry, MediaCensusIndexer}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import responses.{GenericResponse, ObjectListResponse}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import io.circe.generic.auto._
import io.circe.syntax._


@Singleton
class DataController @Inject() (cc:ControllerComponents, config:Configuration, esClientMgr:ESClientManager) extends AbstractController(cc) with Circe with ZonedDateTimeEncoder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.circe._

  private val logger = LoggerFactory.getLogger(getClass)

  private val esClient = esClientMgr.getCachedClient()
  private val indexName = config.get[String]("elasticsearch.indexName")
  private val indexer = new MediaCensusIndexer(indexName)

  def toBackUp(forStorage:Option[String], startAt:Int, limit:Int) = Action.async {
    val queryTerms = Seq(
      forStorage.map(strg=>matchQuery("sourceStorage.keyword", strg)),
      Some(rangeQuery("replicaCount").lt(2))
    ).collect({case Some(term)=>term})

    esClient.execute {
      search(indexName) query boolQuery().must(queryTerms) limit(limit) start(startAt) sortByFieldDesc("originalSource.ctime")
    }.map(result=>{
      if(result.isError){
        logger.error(s"Could not query elastic search: ${result.error}")
        InternalServerError(GenericResponse("error",result.error.toString).asJson)
      } else {
        Ok(ObjectListResponse("ok","census-entry",result.result.to[MediaCensusEntry],result.result.totalHits.toInt).asJson)
      }
    })
  }
}
