package controllers

import helpers.ESClientManager
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.Configuration
import responses.{GenericResponse, HistogramDataResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class StatsController @Inject() (cc:ControllerComponents, config:Configuration, esClientMgr:ESClientManager) extends AbstractController(cc) with Circe {
  import com.sksamuel.elastic4s.http.ElasticDsl._

  private val logger = LoggerFactory.getLogger(getClass)

  private val esClient = esClientMgr.getCachedClient()
  private val indexName = config.get[String]("elasticsearch.indexName")

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
}
