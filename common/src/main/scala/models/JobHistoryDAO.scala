package models

import java.time.ZonedDateTime
import java.util.UUID

import com.sksamuel.elastic4s.ElasticDate
import com.sksamuel.elastic4s.http.ElasticClient
import helpers.ZonedDateTimeEncoder
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global

class JobHistoryDAO(esClient:ElasticClient, indexName:String) extends ZonedDateTimeEncoder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.circe._

  /**
    * save the provided entry to the index
    * @param entry [[JobHistory]] entry to save
    * @return a Future with either an error object or the new document version as a Long
    */
  def put(entry:JobHistory) = esClient.execute {
    update(entry.jobId.toString).in(s"$indexName/jobHistory").docAsUpsert(entry)
  }.map(response=>{
    if(response.isError){
      Left(response.error)
    } else {
      Right(response.result.version)
    }
  })

  /**
    * get the job information for the provided ID
    * @param id UUID of the job
    * @return a Future, with either an error object or an Option containing the [[JobHistory]] for the given job if it exists
    */
  def jobForUuid(id:UUID) = esClient.execute {
    get(id.toString).from(indexName, "jobHistory")
  }.map(response=>{
    if(response.isError){
      Left(response.error)
    } else {
      if(response.result.found)
        Right(Some(response.result.to[JobHistory]))
      else
        Right(None)
    }
  })

  /**
    *retrieve a set of all [[JobHistory]] objects between the two times provided
    * @param startingTime ZonedDateTime representing the start time for the window. If None, then items are returned regardless
    *                     of start time
    * @param endingTime ZonedDateTime representing the ending time for the window. If not set, then defaults to "now"
    * @return a Future, with either an error object or a List of [[JobHistory]]
    */
  def jobsForTimespan(startingTime:Option[ZonedDateTime], endingTime:ZonedDateTime=ZonedDateTime.now()) = {
    val queryList = Seq(
      startingTime.map(actualStartingTime=>
        rangeQuery("startingTime").gte(ElasticDate.fromTimestamp(actualStartingTime.toEpochSecond))
      ),
      Some(rangeQuery("endingTime").lte(ElasticDate.fromTimestamp(endingTime.toEpochSecond)))
    ).collect({case Some(q)=>q})
    esClient.execute {
      search(indexName) query {
        boolQuery().must(queryList)
      } sortByFieldDesc "startingTime"
    }.map(response=>{
      if(response.isError){
        Left(response.error)
      } else {
        Right(response.result.to[JobHistory])
      }
    })
  }

  /**
    * retrieve the latest [[JobHistory]]
    * @param didComplete if true, the latest history with a completion date, if false the latest history without. If None then
    *                    just the latest history regardless of completion state.
    * @return a Future, with either an error object or an Option containing the [[JobHistory]] if it exists
    */
  def mostRecentJob(didComplete:Option[Boolean]) = {
    val queryDefn = didComplete match {
      case Some(true)=>existsQuery("endingTime")
      case Some(false)=>not(existsQuery("endingTime"))
      case None=>matchAllQuery()
    }

    esClient.execute {
      search(indexName) query queryDefn sortByFieldDesc "startingTime" limit 1
    }.map(response=>{
      if(response.isError){
        Left(response.error)
      } else {
        Right(response.result.to[JobHistory].headOption)
      }
    })
  }
}
