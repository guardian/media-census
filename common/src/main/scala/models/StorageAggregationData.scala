package models

import scala.util.{Failure, Success, Try}

case class StorageAggregationData(storage:String, totalHits:Int, states:Seq[StateAggregationData])

case class StateAggregationData(state:String, count:Int, totalSize:Double)

object StorageAggregationData {
  def mapToSubentry(map:Map[String,Any]) = Try {
    StateAggregationData(map("key").asInstanceOf[String], map("doc_count").asInstanceOf[Int], map("totalSize").asInstanceOf[Map[String,Double]]("value"))
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

  def entryForElement(elem: Map[String, Any]) =
    subEntriesForElement(elem("state").asInstanceOf[Map[String,Any]]).map(elems=>StorageAggregationData(elem("key").asInstanceOf[String], elem("doc_count").asInstanceOf[Int], elems))

  def fromRawAggregateMap(data:Map[String,Any]):Try[Seq[StorageAggregationData]] = Try {
    val bucketsData = data("buckets").asInstanceOf[List[Map[String,Any]]]

    val raw = bucketsData.map(elem=>entryForElement(elem))
    val failures=  raw.collect({case Left(errs)=>errs}).flatten

    if(failures.nonEmpty){
      throw failures.head
    } else {
      raw.collect({ case Right(elem) => elem })
    }
  }
}