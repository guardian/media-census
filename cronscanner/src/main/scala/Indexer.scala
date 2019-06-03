import akka.actor.ActorRefFactory
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.http.ElasticClient
import helpers.CensusEntryRequestBuilder
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import models.{JobHistory, MediaCensusEntry}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Indexer(override val indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends CensusEntryRequestBuilder {
  import com.sksamuel.elastic4s.http.ElasticDsl._

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
        Right(result.result.aggregationsAsMap("replicaCount").asInstanceOf[Map[String,Any]])
      }
    })

  def calculateStats(client:ElasticClient, prevJobHistory:JobHistory) = {
    Future.sequence(Seq(
      getReplicaStats(client)
    )).map(resultSeq=> {
      val errors = resultSeq.collect({case Left(err)=>err})
      if(errors.nonEmpty){
        Left(errors)
      } else {
        val replicaStats = resultSeq.head.right.get   //safe because the previous check ensures that there are no Lefts at this point.
        Right()
      }
    })
  }
}
