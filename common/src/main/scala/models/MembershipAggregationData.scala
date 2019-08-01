package models

/**
  * this package provides data models and helper functions to make it easier to deal with item membership stats from ElasticSearch
  */

import java.time.ZonedDateTime

import scala.util.{Failure, Success, Try}

/**
  * a single element in a time histogram of documents
  * @param date time of the datapoint (ZonedDateTime)
  * @param doc_count count of documents from this time point
  */
case class TimeBreakdownData(date:ZonedDateTime, doc_count:Long)

/**
  * overall aggregation of item membership stats
  * @param totalCount the total number of documents scanned
  * @param noMembership the number that had no item/shape membership
  * @param totalSize sum of the sizes of files that had no item/shape membership
  * @param states the count of files in each state (OPEN, CLOSED, MISSING, etc.) as a sequence of [[StateAggregationData]]
  * @param noMembershipTimeBreakdown a sequence of [[TimeBreakdownData]] showing how the items creation times are distributed
  */
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

  /**
    * retrueve time breakdown data from the data map
    * @param content sub-map of the aggregate data from Elassticsrearch, containing a list of "buckets" which in turn contains items with "key_ast-sstring" giving
    *                the timestamp and doc_count giving the document count
    * @return a sequence of [[TimeBreakdownData]]
    */
  def timeBreakdownData(content: Map[String, Any]):Seq[TimeBreakdownData] = {
    content("buckets").asInstanceOf[List[Map[String,Any]]].map(bucketEntry=>TimeBreakdownData(
      ZonedDateTime.parse(bucketEntry("key_as_string").asInstanceOf[String]),
      bucketEntry("doc_count").asInstanceOf[Int]
    ))
  }

  /**
    * converts the raw Map[String,Any] into a sequence of MembershipAggregationData, on enetry for each file state.
    * @param elem
    * @param totalCount
    * @return
    */
  def entryForElement(elem: Map[String, Any], totalCount:Long) =
    subEntriesForElement(elem("state").asInstanceOf[Map[String,Any]]).map(subElems=>MembershipAggregationData(
      totalCount,
      elem("doc_count").asInstanceOf[Int],
      elem("totalSize").asInstanceOf[Map[String,Double]]("value"),
      subElems,
      timeBreakdownData(elem("timestamp").asInstanceOf[Map[String,Any]]),
      )
    )

  /**
    * converts the raw Map[String,Any] from Elasticsearch into our structured data format, [[MembershipAggregationData]]
    * @param itemsData  aggregate map, from ES response
    * @param totalCount total count of items, from ES response
    * @return a Try with either an error or the MembershipAggregationData
    */
  def fromRawAggregateMap(itemsData:Map[String,Any], totalCount:Long):Try[MembershipAggregationData] = Try {
    entryForElement(itemsData,totalCount) match {
      case Right(result)=>result
      case Left(errList)=>throw errList.head
    }
  }
}