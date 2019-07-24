package models

import java.time.ZonedDateTime

import scala.util.{Failure, Success, Try}
case class TimeBreakdownData(date:ZonedDateTime, doc_count:Long)

case class MembershipAggregationData (totalCount:Long, noMembership:Double,totalSize:Double,states:Seq[StateAggregationData], noMembershipTimeBreakdown:Seq[TimeBreakdownData])

object MembershipAggregationData {
  def mapToSubentry(map:Map[String,Any]) = Try {
    StateAggregationData(map("key").asInstanceOf[String], map("doc_count").asInstanceOf[Int], map("size").asInstanceOf[Map[String,Double]]("value"))
  }

  def subEntriesForElement(subElem: Map[String, Any]) = {
    val bucketsData = subElem("buckets").asInstanceOf[List[Map[String,Any]]]

    val raw = bucketsData.map(elem=>mapToSubentry(elem))
    val failures = raw.collect({case Failure(err)=>err})
    if(failures.nonEmpty){
      Left(failures)
    } else {
      Right(raw.collect({ case Success(elem) => elem }))
    }
  }

  def timeBreakdownData(content: Map[String, Any]):Seq[TimeBreakdownData] = {
    content("buckets").asInstanceOf[List[Map[String,Any]]].map(bucketEntry=>TimeBreakdownData(
      ZonedDateTime.parse(bucketEntry("key_as_string").asInstanceOf[String]),
      bucketEntry("doc_count").asInstanceOf[Int]
    ))
  }

  def entryForElement(elem: Map[String, Any], totalCount:Long) =
    subEntriesForElement(elem("state").asInstanceOf[Map[String,Any]]).map(subElems=>MembershipAggregationData(
      totalCount,
      elem("doc_count").asInstanceOf[Int],
      elem("totalSize").asInstanceOf[Map[String,Double]]("value"),
      subElems,
      timeBreakdownData(elem("timestamp").asInstanceOf[Map[String,Any]]),
      )
    )

  def fromRawAggregateMap(itemsData:Map[String,Any], totalCount:Long):Try[MembershipAggregationData] = Try {
    entryForElement(itemsData,totalCount) match {
      case Right(result)=>result
      case Left(errList)=>throw errList.head
    }
  }
}