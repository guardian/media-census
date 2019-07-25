package models

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

import akka.actor.ActorRefFactory
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDate
import com.sksamuel.elastic4s.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.http.ElasticClient
import helpers.ZonedDateTimeEncoder
import org.slf4j.LoggerFactory
import vidispine.{VSFile, VSFileStateEncoder}
import com.sksamuel.elastic4s.streams.RequestBuilder
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
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

  /**
    * returns the total count of items in the index
    * @param esClient
    * @return
    */
  def totalCount(esClient:ElasticClient) = esClient.execute {
    search(indexName) query matchAllQuery() limit(0)
  }.map(result=>{
    if(result.isError){
      Left(result.error.toString)
    } else {
      Right(result.result.totalHits)
    }
  })

  def aggregateByMembership(esClient:ElasticClient) = esClient.execute {
    search(indexName) aggregations {
      missingAgg("no_membership","membership.itemId.keyword")
        .subAggregations(
          sumAgg("totalSize","size"),
          termsAgg("state","state.keyword").subAggregations(sumAgg("size","size")),
          dateHistogramAgg("timestamp","timestamp").interval(FiniteDuration(30,TimeUnit.DAYS))
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

  def getResults(esClient:ElasticClient, startingTime:Option[ZonedDateTime],endingTime:Option[ZonedDateTime], resultsLimit:Option[Int], orphanOnly:Boolean) = {
      val maybeTimeQuery = startingTime.flatMap(actualStartingTime=>endingTime.map(actualEndingTime=>
        rangeQuery("timestamp").gt(ElasticDate(actualStartingTime.toString)).lte(ElasticDate(actualEndingTime.toString))
      ))
    val maybeOrphanQuery = if(orphanOnly) Some(boolQuery().withNot(existsQuery("membership.itemId"))) else None

    val queryList = Seq(maybeTimeQuery, maybeOrphanQuery).collect({case Some(q)=>q})

    val actualResultsLimit = resultsLimit.getOrElse(100)

    esClient.execute {
      search(indexName) query boolQuery().must(queryList) limit actualResultsLimit
    }.map(result=>{
      if(result.isError){
        Left(result.error.toString)
      } else {
        Right((result.result.to[VSFile], result.result.totalHits))
      }
    })
  }
}
