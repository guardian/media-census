package models

import akka.actor.ActorRefFactory
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.elastic4s.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.http.ElasticClient
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.streams.RequestBuilder
import helpers.ZonedDateTimeEncoder
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global


class PlutoProjectIndexer(indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends ZonedDateTimeEncoder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  private val logger = LoggerFactory.getLogger(getClass)

  implicit val indexBuilder = new RequestBuilder[PlutoProject] {
    import com.sksamuel.elastic4s.circe._
    import com.sksamuel.elastic4s.http.ElasticDsl._

    override def request(entry: PlutoProject): BulkCompatibleRequest = update(entry.projectVSID()).in(s"$indexName/plutoproject").docAsUpsert(entry)
  }

  def getIndexSink(client:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    Sink.fromSubscriber(client.subscriber[PlutoProject](batchSize, concurrentBatches))
  }

  def getIndexSource(client:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = Source.fromPublisher(
    client.publisher(search(indexName) sortByFieldAsc "originalSource.ctime" scroll "5m")
  )

  /**
    * Check if the index exists
    * @param client ElasticClient object
    * @return a Future, with a Response object indicating whether the index exists or not.
    */
  def checkIndex(client:ElasticClient) = {
    client.execute {
      indexExists(indexName)
    }
  }
}
