package vidispine

import java.time.ZonedDateTime

import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

case class VSFile(vsid:String, path:String, uri:String, state:VSFileState.Value, size:Long, hash:Option[String], timestamp:ZonedDateTime,refreshFlag:Int,storage:String, metadata:Option[Map[String,String]])

object VSFile {
  def metadataDictFromNodes(metadataNode:NodeSeq):Try[Map[String,String]] = Try {
    (metadataNode \ "field").map(fieldNode=>
      ((fieldNode \ "key").text, (fieldNode \ "value").text)
    ).toMap
  }

  def fromXml(xmlNode:NodeSeq):Try[VSFile] = Try {
    new VSFile(
      (xmlNode \ "id").text,
      (xmlNode \ "path").text,
      (xmlNode \ "uri").text,
      VSFileState.withName((xmlNode \ "state").text),
      (xmlNode \ "size").text.toLong,
      Option((xmlNode \ "hash").text),
      ZonedDateTime.parse((xmlNode \ "timestamp").text),
      (xmlNode \ "refreshFlag").text.toInt,
      (xmlNode \ "storage").text,
      metadataDictFromNodes(xmlNode \ "metadata") match {
        case Success(mdDict)=>Some(mdDict)
        case Failure(err)=>None
      }
    )
  }
}