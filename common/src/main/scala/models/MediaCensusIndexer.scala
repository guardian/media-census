package models

import akka.actor.ActorRefFactory
import akka.stream.SourceShape
import akka.stream.scaladsl.{GraphDSL, Sink, Source}
import com.sksamuel.elastic4s.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.http.ElasticClient
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.aggs.SumAggregation
import com.sksamuel.elastic4s.searches.queries.Query
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.streams.RequestBuilder
import helpers.CensusEntryRequestBuilder
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class MediaCensusIndexer(override val indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends CensusEntryRequestBuilder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  private val logger = LoggerFactory.getLogger(getClass)

  def getIndexSink(client:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    Sink.fromSubscriber(client.subscriber[MediaCensusEntry](batchSize, concurrentBatches))
  }

  def getIndexSource(client:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = Source.fromPublisher(
    client.publisher(search(indexName) sortByFieldAsc "originalSource.ctime" scroll "5m")
  )

  def getDeleteSink(client:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    implicit val deleteBuilder = new RequestBuilder[MediaCensusEntry] {
      import com.sksamuel.elastic4s.http.ElasticDsl._

      override def request(t: MediaCensusEntry): BulkCompatibleRequest = deleteById(indexName, "censusentry", t.originalSource.id.toString)
    }

    Sink.fromSubscriber(client.subscriber[MediaCensusEntry](batchSize, concurrentBatches)(deleteBuilder, actorRefFactory))
  }

  /**
    * returns an Akka streams source that emits [[MediaCensusEntry]] objects matching the given query
    * @param client Elastic4s HttpClient obkect
    * @param query Elastic4s SearchRequest object. This must contain a "scroll" term.
    * @param actorRefFactory implicitly provided ActorRefFactory for streams.
    * @return a partial graph consisting of the Source
    */
  def getSearchSource(client:ElasticClient, query:SearchRequest)(implicit actorRefFactory:ActorRefFactory) = {
    import com.sksamuel.elastic4s.circe._

    val src = Source.fromPublisher(client.publisher(query))
    src.map(_.to[MediaCensusEntry]).async
  }

  /**
    * check if the index exists
    * @param client ElasticClient object
    * @return a Future, with a Response object indicating whether the index exists or not.
    */
  def checkIndex(client:ElasticClient) = {
    client.execute {
      indexExists(indexName)
    }
  }

  /**
    * checks if the given entry exists in the index.
    * @param esClient elastic client
    * @param entryId entry to look for
    * @return a Future, with either an error object or a Boolean indicating whether the entry exists or not
    */
  def doesEntryExist(esClient:ElasticClient, entryId:String) = esClient.execute {
    get(indexName,"censusentry",entryId)
  }.map(response=>{
    if(response.isError){
      Left(response.error)
    } else {
      Right(response.result.found)
    }
  })

  def getReplicaStats(esClient:ElasticClient) =
    esClient.execute {
      search(indexName) aggs {
        histogramAggregation("replicaCount")
          .field("replicaCount")
          .interval(1)
          .minDocCount(0)
          .subAggregations(SumAggregation("totalSize").field("originalSource.size"))
      }
    }.map(result=>{
      if(result.isError){
        Left(result.error.toString)
      } else {
        val rawData = result.result.aggregationsAsMap("replicaCount").asInstanceOf[Map[String,Any]]
        logger.debug(s"fromEsData: incoming data is $rawData")
        val keyData = rawData("buckets").asInstanceOf[List[Map[String,Any]]]

        Right(keyData.map(entry=>StatsEntry(
          entry("key").asInstanceOf[Double].toString,
          entry("doc_count").asInstanceOf[Int].toLong,
          entry("totalSize").asInstanceOf[Map[String,Any]]("value").asInstanceOf[Double].toLong
        )))
      }
    })

  /**
    * get the total count of files that exist in VS but are not attached to items
    * @param esClient
    * @return
    */
  def getUnattachedCount(esClient:ElasticClient) = esClient.execute {
    search(indexName) query boolQuery().must(
      not(existsQuery("vsItemId")),
      existsQuery("vsFileId")
    ) limit 0
  }.map(response=>{
    if(response.isError){
      Left(response.error.toString)
    } else {
      Right(response.result.hits.total)
    }
  })

  def getUnimportedCount(esClient:ElasticClient) = esClient.execute {
    search(indexName) query boolQuery().must(
      not(existsQuery("vsItemId")),
      not(existsQuery("vsFileId"))
    ) limit 0
  }.map(response=>{
    if(response.isError){
      Left(response.error.toString)
    } else {
      Right(response.result.hits.total)
    }
  })

  //def removeZeroBuckets(data: Map[Double, Int]) = data.filter(entry=>entry._2!=0)
  def removeZeroBuckets(data:List[StatsEntry]) = data.filter(_.key.toDouble!=0.0)

  def calculateStatsRaw(client:ElasticClient, includeZeroes:Boolean=true) =
    Future.sequence(Seq(
      getReplicaStats(client),
      getUnattachedCount(client),
      getUnimportedCount(client)
    )).map(resultSeq=> {
      val errors = resultSeq.collect({case Left(err)=>err})
      if(errors.nonEmpty){
        Left(errors)
      } else {
        val replicaStats = resultSeq.head.right.get.asInstanceOf[List[StatsEntry]] //safe because the previous check ensures that there are no Lefts at this point.
        val unattachedCount = resultSeq(1).right.get.asInstanceOf[Long]
        val unimportedCount = resultSeq(2).right.get.asInstanceOf[Long]
        val finalReplicaStats = if(includeZeroes) replicaStats else removeZeroBuckets(replicaStats)

        Right(finalReplicaStats, unattachedCount, unimportedCount)
      }
    })

  /**
    * filters out a list of StatsEntries whose (string) keys have a numeric value greater than or equal to the parameter
    * `gte`. Entries with keys that don't convert to double are ignored.
    * @param data data to filter
    * @param gte return entries whose keys are greater than or equal to this
    * @return the filtered list of StatsEntry
    */
  def filterNumerically(data:List[StatsEntry], gte:Double) = {
    data.filter(entry=>{
      Try {
        entry.key.toDouble
      } match {
        case Success(floatVal)=>floatVal
        case Failure(_)=>0.0
      }
    } >= gte)
  }

  /**
    * sums the count of the provided list of StatsEntry
    * @param data list to sum
    * @return the sum of the "count" field
    */
  def sumStats(data:List[StatsEntry]) = data.foldLeft(0)((acc,entry)=>acc+entry.count.toInt)

  /**
    * updates the provided [[JobHistory]] model with current backup stats
    * @param client ElasticSearch client object
    * @param prevJobHistory pre-existing JobHistory object
    * @return a Future with either the updated JobHistory object or
    */
  def calculateStats(client:ElasticClient, prevJobHistory:JobHistory) =
    calculateStatsRaw(client).map({
      case Right(resultTuple)=>
        val replicaStats = resultTuple._1
        val unattachedCount = resultTuple._2
        val unimportedCount = resultTuple._3

        val updatedJobHistory = prevJobHistory.copy(
          noBackupsCount =  replicaStats.find(_.key=="1.0").map(_.count.toInt).getOrElse(0),
          partialBackupsCount = replicaStats.find(_.key=="2.0").map(_.count.toInt).getOrElse(0),
          fullBackupsCount = sumStats(filterNumerically(replicaStats,3.0)),
          unimportedCount = unimportedCount,
          unattachedCount = unattachedCount
        )

        Right(updatedJobHistory)

      case Left(err)=>Left(err)
    })

}
