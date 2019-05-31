import akka.actor.ActorRefFactory
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.http.HttpClient
import helpers.CensusEntryRequestBuilder
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import models.MediaCensusEntry

class Indexer(override val indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends CensusEntryRequestBuilder {
  import com.sksamuel.elastic4s.http.ElasticDsl._

  def getIndexSink(client:HttpClient)(implicit actorRefFactory: ActorRefFactory) = {
    Sink.fromSubscriber(client.subscriber[MediaCensusEntry](batchSize, concurrentBatches))
  }

  def checkIndex(client:HttpClient) = {
    client.execute {
      indexExists(indexName)
    }
  }
}
