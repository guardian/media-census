package models

import java.util.concurrent.TimeUnit

import akka.actor.ActorRefFactory
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.elastic4s.http.ElasticClient
import com.sksamuel.elastic4s.searches.queries.Query
import com.sksamuel.elastic4s.streams.RequestBuilder
import org.slf4j.LoggerFactory
import vidispine.VSFile
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * indexer function for ArchiveNearlineEntry
  * @param indexName
  * @param batchSize
  * @param concurrentBatches
  */
class ArchiveNearlineEntryIndexer(val indexName:String, batchSize:Int=20, concurrentBatches:Int=2)  {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.streams.ReactiveElastic._
  import com.sksamuel.elastic4s.circe._

  private val logger = LoggerFactory.getLogger(getClass)

  def getSink(esClient:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    implicit val builder:RequestBuilder[ArchiveNearlineEntry] = (t: ArchiveNearlineEntry) => update(t.omUri) in s"$indexName/archivenl" docAsUpsert t
    Sink.fromSubscriber(esClient.subscriber[ArchiveNearlineEntry](batchSize=batchSize, concurrentRequests = concurrentBatches))
  }

  /**
    * return an akka streams source for VSFile hits based on the given query parameters. You can directly .map() this to a VSFile:
    * source.map(_.as[VSFile]) provided that you have circe and the relevant elastic4s implicits in scope
    * @param esClient elasticsearch client object
    * @param q a Sequence of elasticsearch queries. All of these must hold true for the item to be returned
    * @param actorRefFactory implicitly provided ActorRefFactory, this normally comes from the ActorSystem.
    * @return a stream Source that yields SearchHits. You can map this directly to VSFile objects, as indicated above
    */
  def getSource(esClient:ElasticClient, q:Seq[Query])(implicit actorRefFactory: ActorRefFactory) = Source.fromPublisher(
    esClient.publisher(search(indexName) query boolQuery().withMust(q) scroll FiniteDuration(5, TimeUnit.MINUTES))
  )

  def statsByCollection(esClient:ElasticClient) = esClient.execute {
    search(indexName) aggs (
      termsAgg("byCollection", "archiveHunterCollection.keyword").subaggs {
        termsAgg("archiveHunterDeleted", "archiveHunterDeleted")
        termsAgg("vsStorage", "vsStorage.keyword")
        sumAgg("size","size")
      },
      missingAgg("noCollection", "archiveHunterCollection").subaggs {
        sumAgg("size","size")
      }
    )
  } map(response=>{
    logger.debug(s"Got response: ${response.result.aggregations}")
    if(response.isError){
      Left(response.error)
    } else {
      Right(response.result.aggregations)
    }
  })

  def statsByVSStorage(esClient:ElasticClient) = esClient.execute {
    search(indexName) aggs {
      termsAgg("vsStorage", "vsStorage.keyword").subaggs {
        termsAgg("archiveHunterDeleted", "archiveHunterDeleted")
        termsAgg("byCollection","archiveHunterCollection.keyword")
      }
    }
  } map(response=>{
    if(response.isError){
      Left(response.error)
    } else {
      Right(response.result.aggregations)
    }
  })

  def statsBinary(esClient:ElasticClient) = esClient.execute {
    search(indexName) aggs {
      missingAgg("noCollection", "archiveHuntercollection").subaggs {
        sumAgg("size", "size")
      }
    }
  } map(response=>{
    logger.debug(s"Got response: ${response.result.aggregations}")
    if(response.isError){
      Left(response.error)
    } else {
      Right(response.result.aggregations)
    }
  })
}
