package models

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

import akka.actor.ActorRefFactory
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.elastic4s.ElasticDate
import com.sksamuel.elastic4s.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.http.ElasticClient
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.queries.Query
import helpers.ZonedDateTimeEncoder
import org.slf4j.LoggerFactory
import vidispine.{VSFile, VSFileStateEncoder}
import com.sksamuel.elastic4s.streams.RequestBuilder
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class VSFileIndexer(val indexName:String, batchSize:Int=20, concurrentBatches:Int=2) extends ZonedDateTimeEncoder with VSFileStateEncoder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.streams.ReactiveElastic._
  import com.sksamuel.elastic4s.circe._

  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * return an akka streams sink that accepts [[VSFile]] objects and writes them to the index, overwriting any existing record
    * @param esClient ElasticClient object to access the cluster
    * @param actorRefFactory implicitly provided Actor System
    * @return the sink
    */
  def getSink(esClient:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    implicit val builder:RequestBuilder[VSFile] = (t: VSFile) => update(t.vsid) in s"$indexName/vsfile" docAsUpsert t
    Sink.fromSubscriber(esClient.subscriber[VSFile](batchSize=batchSize, concurrentRequests = concurrentBatches))
  }

  /**
    * return an akka streams sink that accepts [[VSFile]] objects and performs a partial update or insert on it.
    * the `archiveHunterId` field is always left out (unchanged) and any optional fields that are None are also left out
    * @param esClient ElasticClient object to access the cluster
    * @param actorRefFactory implicitly provided Actor System
    * @return the sink
    */
  def getPartialUpdateSink(esClient:ElasticClient)(implicit actorRefFactory: ActorRefFactory) = {
    implicit val builder: RequestBuilder[VSFile] = (t: VSFile) => update(t.vsid) in s"$indexName/vsfile" docAsUpsert t.partialMap()
    Sink.fromSubscriber(esClient.subscriber[VSFile](batchSize=batchSize, concurrentRequests = concurrentBatches))
  }

  /**
  * return an akka streams Sink that will delete each item coming through. Use with care!!
  * @param esClient ElasticSearch client object
  * @param actorRefFactory implicitly provided ActorRefFactory, if you have an implicit ActorSystem this will work
  * @return Sink that receives VSFile objects from the index and deletes them!
  */
  def deleteSink(esClient:ElasticClient, reallyDelete:Boolean)(implicit actorRefFactory: ActorRefFactory) = {
    implicit val builder:RequestBuilder[VSFile] = (t: VSFile) => delete(t.vsid) from s"$indexName/vsfile"

    if(reallyDelete) {
      Sink.fromSubscriber(esClient.subscriber[VSFile](batchSize = batchSize, concurrentRequests = concurrentBatches))
    } else {
      Sink.foreach[VSFile](elem=>logger.warn(s"I would delete the index record for ${elem.path} from ${elem.storage} if reallyDelete were true"))
    }
  }

  /**
    * return an akka streams source for VSFile hits based on the given query parameters. You can directly .map() this to a VSFile:
    * source.map(_.as[VSFile]) provided that you have circe and the relevant elastic4s implicits in scope
    * @param esClient elasticsearch client object
    * @param q a Sequence of elasticsearch queries. All of these must hold true for the item to be returned
    * @param actorRefFactory implicitly provided ActorRefFactory, this normally comes from the ActorSystem.
    * @return a stream Source that yields SearchHits. You can map this directly to VSFile objects, as indicated above
    */
  def getSource(esClient:ElasticClient, q:Seq[Query], limit:Option[Int])(implicit actorRefFactory: ActorRefFactory) = {
    val params = search(indexName) query boolQuery().withMust(q)
    val finalParams = limit match {
      case Some(actualLimit)=>
        logger.debug(s"Setting request limit $actualLimit")
        params limit actualLimit
      case None=>params
    }
    Source.fromPublisher(
      esClient.publisher(finalParams scroll FiniteDuration(5, TimeUnit.MINUTES))
    )
  }

  /**
    * return an akka streams source that only yields out VSFile hits that are not a member of any item
    * @param esClient
    * @param actorRefFactory
    * @return
    */
  def getOrphansSource(esClient:ElasticClient,storageId:Option[String], limit:Option[Int])(implicit actorRefFactory: ActorRefFactory) = {
    val queries = Seq(
      Some(boolQuery.not(existsQuery("membership.itemId"))),
      storageId.map(sid=>matchQuery("storage.keyword",sid))
    ).collect({case Some(q)=>q})
    getSource(esClient, queries, limit)
  }

  /**
    * get aggregate data from the files index for both overall file state and sizes
    * @param esClient ElasticSearch client
    * @return either a Left with a string indicating the error, or a Right containing [[StorageAggregationData]]
    */
  def aggregateByStateAndStorage(esClient:ElasticClient) = esClient.execute {
    search(indexName) aggregations {
      termsAgg("storage","storage.keyword")
        .subAggregations(
          sumAgg("totalSize","size"),
          termsAgg("state","state.keyword").subAggregations(sumAgg("size","size"))
        )
    }
  }.map(result=>{
    if(result.isError){
      Left(result.error.toString)
    } else {
      logger.debug(s"Got raw aggregation data: ${result.result.aggregationsAsMap}")
      StorageAggregationData.fromRawAggregateMap(result.result.aggregationsAsMap("storage").asInstanceOf[Map[String,Any]]) match {
        case Success(aggregateData)=>Right(aggregateData)
        case Failure(err)=>
          logger.error(s"Could not process aggregate data", err)
          Left(err.toString)
      }
    }
  })

  /**
    * returns the total count of items in the index
    * @param esClient
    * @return
    */
  def totalCount(esClient:ElasticClient) = esClient.execute {
    search(indexName) query matchAllQuery() limit(0)
  }.map(result=>{
    if(result.isError){
      Left(result.error.toString)
    } else {
      Right(result.result.totalHits)
    }
  })

  /**
    * build a report for the overall amount of media that is not attached to any item compared to the total
    * @param esClient Elasticsearch Client
    * @return either a Left with an error string or a Right with [[MembershipAggregationData]]
    */
  def aggregateByMembership(esClient:ElasticClient) = esClient.execute {
    search(indexName) aggregations {
      missingAgg("no_membership","membership.itemId.keyword")
        .subAggregations(
          sumAgg("totalSize","size"),
          termsAgg("state","state.keyword").subAggregations(sumAgg("size","size")),
          dateHistogramAgg("timestamp","timestamp").interval(FiniteDuration(30,TimeUnit.DAYS))
        )
    }
  }.map(result=>{
    if(result.isError){
      Left(result.error.toString)
    } else {
      logger.debug(s"Got raw aggregation data: ${result.result.aggregationsAsMap}")
      MembershipAggregationData.fromRawAggregateMap(result.result.aggregationsAsMap("no_membership").asInstanceOf[Map[String,Any]], result.result.totalHits) match {
        case Success(aggregateData)=>Right(aggregateData)
        case Failure(err)=>
          logger.error(s"Could not process aggregate data", err)
          Left(err.toString)
      }
    }
  })

  /**
    * searches the VSFile index
    * @param esClient elasticsearch client
    * @param startingTime optional ZonedDateTime giving the start of a time window to search in
    * @param endingTime optional ZonedDateTime giving the end of a time window to search in
    * @param resultsLimit optionally limit the results to this number. Defaults to 10 if not given
    * @param orphanOnly boolean, if true only return files that have no item membership
    * @return a Future, containing either an error string or a tuple of (resultslist, total_count)
    */
  def getResults(esClient:ElasticClient, startingTime:Option[ZonedDateTime],endingTime:Option[ZonedDateTime], resultsLimit:Option[Int], orphanOnly:Boolean) = {
      val maybeTimeQuery = startingTime.flatMap(actualStartingTime=>endingTime.map(actualEndingTime=>
        rangeQuery("timestamp").gt(ElasticDate(actualStartingTime.toString)).lte(ElasticDate(actualEndingTime.toString))
      ))
    val maybeOrphanQuery = if(orphanOnly) Some(boolQuery().withNot(existsQuery("membership.itemId"))) else None

    val queryList = Seq(maybeTimeQuery, maybeOrphanQuery).collect({case Some(q)=>q})

    val actualResultsLimit = resultsLimit.getOrElse(100)

    esClient.execute {
      search(indexName) query boolQuery().must(queryList) limit actualResultsLimit
    }.map(result=>{
      if(result.isError){
        Left(result.error.toString)
      } else {
        Right((result.result.to[VSFile], result.result.totalHits))
      }
    })
  }

  def getById(esClient:ElasticClient, vsid:String) = {
    esClient.execute {
      get(indexName, "vsfile", vsid)
    }.map(response=>{
      if(response.isError){
        Left(response.error)
      } else {
        Right(response.result.to[VSFile])
      }
    })
  }

  /**
    * obtains statistics for how many items have the "ArchiveHunterId" field set, i.e. are also present in deep archive
    * returns either an error string or Map containing "total_count" and "missing_archive_id_count" fields boths as Long
    * @param esClient
    * @return
    */
  def isArchivedStats(esClient:ElasticClient) = esClient.execute {
    search(indexName) aggs (
      missingAgg("missingArchiveHunterId", "archiveHunterId.keyword") subaggs {
        sumAgg("size","size")
      },
      sumAgg("total_size","size")
    )
  } map(result=>{
    if(result.isError){
      Left(result.error.toString)
    } else {
      val returnedData = result.result.aggregations.data("missingArchiveHunterId").asInstanceOf[Map[String, Any]]
      logger.debug(s"isArchivedStats: Got $returnedData")
      val results = Map(
        "total_count"->result.result.totalHits,
        "missing_archive_id_count"->returnedData("doc_count").asInstanceOf[Int].toLong,
        "missing_archive_id_totalsize"->returnedData("size").asInstanceOf[Map[String,Any]]("value").asInstanceOf[Double].toLong,
        "total_size"->result.result.aggregations.data("total_size").asInstanceOf[Map[String,Any]]("value").asInstanceOf[Double].toLong
      )
      val finalResults = results ++ Map(
        "with_archive_id_count"-> (results("total_count") - results("missing_archive_id_count")),
        "with_archive_id_totalsize"-> (results("total_size") - results("missing_archive_id_totalsize"))
      )
      Right(finalResults)
    }
  })
}
