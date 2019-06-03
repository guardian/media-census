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

  def put(entry:JobHistory) = esClient.execute {
    update(entry.jobId.toString).in(s"$indexName/jobHistory").docAsUpsert(entry)
  }

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
      }
    }.map(response=>{
      if(response.isError){
        Left(response.error)
      } else {
        Right(response.result.to[JobHistory])
      }
    })
  }
}
