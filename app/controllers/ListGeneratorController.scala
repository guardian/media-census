package controllers

import akka.actor.ActorSystem
import helpers.ESClientManager
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.mvc.{AbstractController, ControllerComponents}
import models.MediaCensusIndexer
import play.api.libs.circe.Circe
import responses.GenericResponse
import io.circe.generic.auto._
import io.circe.syntax._

import scala.io.Source

@Singleton
class ListGeneratorController @Inject() (config:Configuration, cc:ControllerComponents, esClientMgr:ESClientManager)(implicit actorSystem:ActorSystem) extends AbstractController(cc) with Circe{
  private val logger = LoggerFactory.getLogger(getClass)
  private val esClient = esClientMgr.getClient()
  private val indexName = config.get[String]("elasticsearch.indexName")
  private val indexer = new MediaCensusIndexer(indexName)

  /**
    * streams a list of filenames that are currently registered as unimported as a text/csv list
    * @return
    */
  def unimportedFileList(include:Option[String],json:Option[String]) = Action {
    import com.sksamuel.elastic4s.http.ElasticDsl._
    val src = indexer.getSearchSource(esClient, search(indexName) query
//      matchAllQuery()
      boolQuery().must(
        not(existsQuery("vsFileId")),
        not(existsQuery("vsItemId")),
        not(existsQuery("vsShapeIds"))
      )
      scroll "5m")

    val finalSource:Either[String,src.Repr[String]] = if(json.isDefined && json.get !="false" && json.get != "no"){
      Right(src.map(_.asJson.noSpaces +"\n"))
    } else {
      include match {
        case None =>
          Right(src.map(entry => s"${entry.originalSource.filepath}/${entry.originalSource.filename}\n"))
        case Some("wide") =>
          Right(src.map(entry => s"${entry.originalSource.filepath}, ${entry.originalSource.filename}, ${entry.replicaCount}, ${entry.originalSource.size}, ${entry.originalSource.ctime}, ${entry.originalSource.mtime}, ${entry.originalSource.atime}\n"))
        case Some(_) =>
          Left(s"Unrecognised parameter")
      }
    }

    finalSource match {
      case Right(s)=>
        val ct = if(json.isDefined && json.get !="false" && json.get != "no"){
          "application/x-ndjson"
        } else {
          "text/csv"
        }
        Ok.chunked(s).as(contentType = ct)
      case Left(errString)=>
        BadRequest(GenericResponse("err",errString).asJson)
    }

  }
}
