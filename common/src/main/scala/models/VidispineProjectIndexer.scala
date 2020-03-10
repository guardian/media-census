package models

import akka.actor.ActorRefFactory
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.elastic4s.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticDsl}
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.sksamuel.elastic4s.circe._
import helpers.ZonedDateTimeEncoder
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class VidispineProjectIndexer(indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends ZonedDateTimeEncoder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  private val logger = LoggerFactory.getLogger(getClass)

  implicit val indexBuilder = new RequestBuilder[VidispineProject] {
    import com.sksamuel.elastic4s.circe._
    import com.sksamuel.elastic4s.http.ElasticDsl._

    override def request(entry: VidispineProject): BulkCompatibleRequest = update(entry.vsid).in(s"$indexName/vidispineproject").docAsUpsert(entry)
  }

  def getIndexSink(client:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    Sink.fromSubscriber(client.subscriber[VidispineProject](batchSize, concurrentBatches))
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

  /**
    * look up a single record from the index
    * @param client ElasticClient object
    * @param projectId vidispine ID to look up
    * @return
    */
  def lookup(client:ElasticClient, projectId:String) =
    client.execute {
      get(projectId).from(indexName)
    }.map(response=>{
      if(response.isError) {
        Left(response.error)
      } else {
        Right(response.result.to[VidispineProject])
      }
    })

  /**
    * perform an efficient bulk lookup on multiple project ids from the index
    * @param client ElasticClient object
    * @param projectIdList list of vidispine IDs to look up
    * @return a Future containing either an ElasticError describing a problem or an IndexedSeq of VidispineProject items
    */
  def lookupBulk(client:ElasticClient, projectIdList:Seq[String]) = {
    val ops = projectIdList.map(projectId=>get(projectId).from(indexName))

    client.execute {
      multiget(ops)
    }
  }.map(response=>{
    if(response.isError) {
      Left(response.error)
    } else {
      println(s"got response: ${response.result.toString}")
      println(s"got ${response.result.size} records")
      Right(response.result.docs.flatMap(rec=>{
        if(rec.found) {
          Some(rec.to[VidispineProject])
        } else {
          None
        }
      }))
    }
  })
}
