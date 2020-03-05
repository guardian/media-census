package models

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.sksamuel.elastic4s.{Hit, HitReader}
import helpers.ZonedDateTimeEncoder
import vidispine.{VSFile, VSFileItemMembership, VSFileState}

import scala.util.{Success, Try}

/*
(vsid:String, path:String, uri:String, state:Option[VSFileState.Value], size:Long, hash:Option[String],
                  timestamp:ZonedDateTime,refreshFlag:Int,storage:String, metadata:Option[Map[String,String]],
                  membership: Option[VSFileItemMembership], archiveHunterId: Option[String], archiveConflict:Option[Boolean]=None)
 */

//implementing this manually because have been having some trouble with the `membership` field being set to an empty Map rather than None
//which breaks the auto-generated reader
trait VSFileHitReader extends ZonedDateTimeEncoder {
  implicit object VSFileHitReader extends HitReader[VSFile] {
    def decodeMembership(rawValues:Map[String,AnyRef]) = {
      if(rawValues.isEmpty){
        None
      } else {
        Some(VSFileItemMembership.fromMap(rawValues))
      }
    }

    override def read(hit: Hit): Try[VSFile] = Try {
      VSFile(
        vsid=hit.sourceAsMap("vsid").toString,
        path=hit.sourceAsMap("path").toString,
        uri=hit.sourceAsMap("uri").toString,
        state=hit.sourceAsMap.get("state").map(s=>VSFileState.withName(s.toString)),
        size=hit.sourceAsMap("size").asInstanceOf[Long],
        hash=hit.sourceAsMap.get("hash").map(_.toString),
        timestamp=ZonedDateTime.parse(hit.sourceAsMap("timestamp").toString, DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        refreshFlag = hit.sourceAsMap("refreshFlag").asInstanceOf[Int],
        storage=hit.sourceAsMap("storage").toString,
        metadata=hit.sourceAsMap.get("metadata").asInstanceOf[Option[Map[String,String]]],
        membership = hit.sourceAsMap.get("membership").map(_.asInstanceOf[Map[String,AnyRef]]).flatMap(decodeMembership),
        archiveHunterId = hit.sourceAsMap.get("archiveHunterId").map(_.toString),
        archiveConflict = hit.sourceAsMap.get("archiveConflict").map(_.asInstanceOf[Boolean])
      )
    }
  }
}