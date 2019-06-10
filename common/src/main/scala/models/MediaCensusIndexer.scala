package models

import akka.actor.ActorRefFactory
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.http.ElasticClient
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.streams.RequestBuilder
import helpers.CensusEntryRequestBuilder
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MediaCensusIndexer(override val indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends CensusEntryRequestBuilder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  private val logger = LoggerFactory.getLogger(getClass)

  def getIndexSink(client:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    Sink.fromSubscriber(client.subscriber[MediaCensusEntry](batchSize, concurrentBatches))
  }

  def getDeleteSink(client:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    implicit val deleteBuilder = new RequestBuilder[AssetSweeperFile] {
      import com.sksamuel.elastic4s.http.ElasticDsl._

      override def request(t: AssetSweeperFile): BulkCompatibleRequest = deleteById(indexName, "censusentry", t.id.toString)
    }

    Sink.fromSubscriber(client.subscriber[AssetSweeperFile](batchSize, concurrentBatches))
  }

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
      }
    }.map(result=>{
      if(result.isError){
        Left(result.error.toString)
      } else {
        val rawData = result.result.aggregationsAsMap("replicaCount").asInstanceOf[Map[String,Any]]
        logger.debug(s"fromEsData: incoming data is $rawData")
        val keyData = rawData("buckets").asInstanceOf[List[Map[String,Any]]]

        Right((
          keyData.map(entry=>entry("key").asInstanceOf[Double]),
          keyData.map(entry=>entry("doc_count").asInstanceOf[Int])
        ))
      }
    }).map(_.map(tuples=>tuples._1.zip(tuples._2).toMap))

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

  def removeZeroBuckets(data: Map[Double, Int]) = data.filter(entry=>entry._2!=0)

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
        val replicaStats = resultSeq.head.right.get.asInstanceOf[Map[Double, Int]] //safe because the previous check ensures that there are no Lefts at this point.
        val unattachedCount = resultSeq(1).right.get.asInstanceOf[Long]
        val unimportedCount = resultSeq(2).right.get.asInstanceOf[Long]
        val finalReplicaStats = if(includeZeroes) replicaStats else removeZeroBuckets(replicaStats)

        Right(finalReplicaStats, unattachedCount, unimportedCount)
      }
    })

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
          noBackupsCount =  replicaStats.getOrElse(1.0, 0),
          partialBackupsCount = replicaStats.getOrElse(2.0,0),
          fullBackupsCount = replicaStats.getOrElse(3.0,0),  //FIXME: should be 3 OR MORE.
          unimportedCount = unimportedCount,
          unattachedCount = unattachedCount
        )

        Right(updatedJobHistory)

      case Left(err)=>Left(err)
    })

}
