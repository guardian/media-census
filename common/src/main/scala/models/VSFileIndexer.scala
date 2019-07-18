package models

import akka.actor.ActorRefFactory
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.http.ElasticClient
import helpers.ZonedDateTimeEncoder
import org.slf4j.LoggerFactory
import vidispine.{VSFile, VSFileStateEncoder}
import com.sksamuel.elastic4s.streams.RequestBuilder
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class VSFileIndexer(val indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends ZonedDateTimeEncoder with VSFileStateEncoder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.streams.ReactiveElastic._
  import com.sksamuel.elastic4s.circe._

  private val logger = LoggerFactory.getLogger(getClass)

  def getSink(esClient:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    implicit val builder:RequestBuilder[VSFile] = (t: VSFile) => update(t.vsid) in s"$indexName/vsfile" docAsUpsert t
    Sink.fromSubscriber(esClient.subscriber[VSFile](batchSize=batchSize, concurrentRequests = concurrentBatches))
  }

  def aggregateByStateAndStorage(esClient:ElasticClient) = esClient.execute {
    search(indexName) aggregations {
      termsAgg("storage","storage.keyword")
        .subAggregations(
          sumAgg("totalSize","size"),
          termsAgg("state","state.keyword").subAggregations(sumAgg("size","size"))
        )

    }
  }.map(result=>{
    if(result.isError){
      Left(result.error.toString)
    } else {
      logger.debug(s"Got raw aggregation data: ${result.result.aggregationsAsMap}")
      StorageAggregationData.fromRawAggregateMap(result.result.aggregationsAsMap("storage").asInstanceOf[Map[String,Any]]) match {
        case Success(aggregateData)=>Right(aggregateData)
        case Failure(err)=>
          logger.error(s"Could not process aggregate data", err)
          Left(err.toString)
      }
    }
  })

  def aggregateByMembership(esClient:ElasticClient) = esClient.execute {
    search(indexName) aggregations {
      missingAgg("no_membership","membership")
        .subAggregations(
          sumAgg("totalSize","size"),
          termsAgg("state","state.keyword").subAggregations(sumAgg("size","size"))
        )
    }
  }.map(result=>{
    if(result.isError){
      Left(result.error.toString)
    } else {
      logger.debug(s"Got raw aggregation data: ${result.result.aggregationsAsMap}")
      MembershipAggregationData.fromRawAggregateMap(result.result.aggregationsAsMap("no_membership").asInstanceOf[Map[String,Any]], result.result.totalHits) match {
        case Success(aggregateData)=>Right(aggregateData)
        case Failure(err)=>
          logger.error(s"Could not process aggregate data", err)
          Left(err.toString)
      }
    }
  })
}
