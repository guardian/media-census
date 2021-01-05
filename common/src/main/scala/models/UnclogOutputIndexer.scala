package models

import akka.NotUsed
import akka.actor.ActorRefFactory
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.elastic4s.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.http.ElasticClient
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.streams.RequestBuilder
import helpers.ZonedDateTimeEncoder
import org.slf4j.LoggerFactory
import vidispine.VSFile

import scala.concurrent.ExecutionContext.Implicits.global


class UnclogOutputIndexer(indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends ZonedDateTimeEncoder {
  import MediaStatusValueEncoder._
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.circe._
  import io.circe.generic.auto._
  private val logger = LoggerFactory.getLogger(getClass)

  implicit val indexBuilder = new RequestBuilder[UnclogOutput] {

    override def request(entry: UnclogOutput): BulkCompatibleRequest = update(entry.VSFileId).in(s"$indexName/file").docAsUpsert(entry)
  }

  def getIndexSink(client:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    Sink.fromSubscriber(client.subscriber[UnclogOutput](batchSize, concurrentBatches))
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
   * creates a streaming source that yields VSFile objects (from the nearline index) for each entry matching the given nearline status
   * @param unclogStatus [[MediaStatusValue]] indicating which category to fetch
   * @param client ElasticSearch client object
   * @param vsFileIndexer VSFileIndexer object to read data from the nearline index
   * @param actorRefFactory implicitly provided ref factory
   * @return a Source that yields VSFile records
   */
  def NearlineEntriesForStatus(unclogStatus: MediaStatusValue.Value,client:ElasticClient, vsFileIndexer:VSFileIndexer)(implicit actorRefFactory: ActorRefFactory):Source[VSFile, NotUsed] = {
    Source.fromGraph(
      Source.fromPublisher(
        client.publisher(search(indexName) query termQuery("MediaStatus.keyword", unclogStatus.toString) sortByFieldAsc "VSFileId.keyword" scroll "5m")
      ).log("nearline-entries-for-status-stream").map(_.to[UnclogOutput])
        .mapAsync(parallelism = 1)(entry=>vsFileIndexer.getById(client, entry.VSFileId).map({
          case Left(err)=>
            logger.error(s"Could not look up vs file ${entry.VSFileId} in nearline index: $err")
            throw new RuntimeException("Could not look up file, see logs for exception")
          case Right(vsFile)=>vsFile
        }))
    )
  }

  def getMediaStatusStats(implicit client:ElasticClient) = client.execute {
    search(indexName) size 0 aggs termsAgg("mediastatus","MediaStatus.keyword")
      .subaggs(
        sumAgg("size","FileSize"),
        termsAgg("projectCount", "ParentCollectionIds.keyword")
      )
  }.map(response=>{
    if(response.isError) {
      Left(response.error)
    } else {
      val aggregateData = response.result.aggregationsAsMap("mediastatus").asInstanceOf[Map[String,Any]]
      logger.debug(s"got aggregate data: $aggregateData")
      val buckets = aggregateData("buckets").asInstanceOf[List[Map[String,Any]]]

      val output = buckets.map(entry=>{
        GenericAggregationValue(
          entry("key").asInstanceOf[String],
          entry("doc_count").asInstanceOf[Int],
          entry("size").asInstanceOf[Map[String,Double]]("value").toLong
        )
      })
      Right(output)
    }
  })

  def recordsForMediaStatus(statusValue: MediaStatusValue.Value, startAt:Int, limit:Int)(implicit client:ElasticClient) = client.execute( {
    search(indexName) query termQuery("MediaStatus.keyword", statusValue.toString) size limit from startAt aggs termsAgg("parent_project", "ParentCollectionIds.keyword")
  }).map(response=>{
    if(response.isError) {
      Left(response.error)
    } else {
      Right((response.result.to[UnclogOutput],response.result.totalHits))
    }
  })

  def projectsForMediaStatus(statusValue: MediaStatusValue.Value)(implicit client:ElasticClient) = client.execute( {
    search(indexName) query termQuery("MediaStatus.keyword", statusValue.toString) size 0 aggs termsAgg("parent_project", "ParentCollectionIds.keyword").subaggs(sumAgg("size","FileSize"))
  }).map(response=>{
    if(response.isError) {
      Left(response.error)
    } else {
      val aggregateData = response.result.aggregationsAsMap("parent_project").asInstanceOf[Map[String,Any]]
      logger.debug(s"got aggregate data: $aggregateData")
      val buckets = aggregateData("buckets").asInstanceOf[List[Map[String,Any]]]

      val output = buckets.map(entry=>{
        GenericAggregationValue(
          entry("key").asInstanceOf[String],
          entry("doc_count").asInstanceOf[Int],
          entry("size").asInstanceOf[Map[String,Double]]("value").toLong
        )
      })
      Right(output)
    }
  })
}
