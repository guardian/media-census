package controllers

import helpers.ESClientManager
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import io.circe.generic.auto._
import io.circe.syntax._
import models.MediaCensusIndexer
import play.api.Configuration
import responses.{GenericResponse, HistogramDataResponse, ObjectGetResponse, ObjectListResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class StatsController @Inject() (cc:ControllerComponents, config:Configuration, esClientMgr:ESClientManager) extends AbstractController(cc) with Circe {
  import com.sksamuel.elastic4s.http.ElasticDsl._

  private val logger = LoggerFactory.getLogger(getClass)

  private val esClient = esClientMgr.getCachedClient()
  private val indexName = config.get[String]("elasticsearch.indexName")

  val mcIndexer = new MediaCensusIndexer(indexName)

  def replicaStats = Action.async {
    esClient.execute {
      search(indexName) aggs {
        histogramAggregation("replicaCount")
          .field("replicaCount")
          .interval(1)
          .minDocCount(0)
      }
    }.map(result=>{
      if(result.isError){
        InternalServerError(GenericResponse("error",result.error.toString).asJson)
      } else {
        val response = HistogramDataResponse.fromEsData[Double, Int](result.result.aggregationsAsMap("replicaCount").asInstanceOf[Map[String,Any]])

        response match {
          case Success(statsData)=>Ok(statsData.asJson)
          case Failure(err)=>InternalServerError(GenericResponse("error",err.toString).asJson)
        }
      }
    })
  }

  def unattachedStats = Action.async {
    mcIndexer.calculateStatsRaw(esClient, includeZeroes = false).map({
      case Left(errSeq)=>
        InternalServerError(ObjectListResponse("db_error","errstring",errSeq, errSeq.length).asJson)
      case Right(statsTuple)=>
        val response = HistogramDataResponse.
          fromMap[Double,Int,Map[String,Long]](statsTuple._1,Some(Map("unimported"->statsTuple._3, "unattached"->statsTuple._2)))

        Ok(response.asJson)
        //Ok(ObjectGetResponse("ok","stats",Map("unattached"->statsTuple._2, "unimported"->statsTuple._3, "replicas"->statsTuple._1)).asJson)
    })
  }
}
