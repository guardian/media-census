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

class VSFileIndexer(val indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends ZonedDateTimeEncoder with VSFileStateEncoder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.streams.ReactiveElastic._
  import com.sksamuel.elastic4s.circe._

  private val logger = LoggerFactory.getLogger(getClass)

  def getSink(esClient:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    implicit val builder:RequestBuilder[VSFile] = new RequestBuilder[VSFile] {
      override def request(t: VSFile): BulkCompatibleRequest = update(t.vsid) in s"$indexName/vsfile" docAsUpsert t
    }
    Sink.fromSubscriber(esClient.subscriber[VSFile](batchSize=batchSize, concurrentRequests = concurrentBatches))
  }
}
