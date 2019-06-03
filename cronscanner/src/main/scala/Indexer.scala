import akka.actor.ActorRefFactory
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.http.ElasticClient
import helpers.CensusEntryRequestBuilder
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import models.{JobHistory, MediaCensusEntry}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Indexer(override val indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends CensusEntryRequestBuilder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  private val logger = LoggerFactory.getLogger(getClass)

  def getIndexSink(client:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    Sink.fromSubscriber(client.subscriber[MediaCensusEntry](batchSize, concurrentBatches))
  }

  def checkIndex(client:ElasticClient) = {
    client.execute {
      indexExists(indexName)
    }
  }

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
    * updates the provided [[JobHistory]] model with current backup stats
    * @param client ElasticSearch client object
    * @param prevJobHistory pre-existing JobHistory object
    * @return a Future with either the updated JobHistory object or
    */
  def calculateStats(client:ElasticClient, prevJobHistory:JobHistory) = {
    Future.sequence(Seq(
      getReplicaStats(client)
    )).map(resultSeq=> {
      val errors = resultSeq.collect({case Left(err)=>err})
      if(errors.nonEmpty){
        Left(errors)
      } else {
        val replicaStats = resultSeq.head.right.get   //safe because the previous check ensures that there are no Lefts at this point.

        val updatedJobHistory = prevJobHistory.copy(
          noBackupsCount =  replicaStats.getOrElse(1.0, 0),
          partialBackupsCount = replicaStats.getOrElse(2.0,0),
          fullBackupsCount = replicaStats.getOrElse(3.0,0)  //FIXME: should be 3 OR MORE.
        )

        Right(updatedJobHistory)
      }
    })
  }
}
