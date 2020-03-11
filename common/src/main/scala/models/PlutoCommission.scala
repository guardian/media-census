package models

import java.time.{LocalDate, ZonedDateTime}
import scala.util.Try
import scala.xml.{Elem, Node}

case class PlutoCommission (vsid:String, name:String, projects:List[String], status:String, scheduled_completion:Option[LocalDate])

object PlutoCommission {
  def extractMetadataValue(metaNode:Node, fieldName:String):Option[String] = {
    val applicableFieldNodes = (metaNode \ "timespan" \ "field").filter(fieldNode=>(fieldNode\"name").text==fieldName)
    applicableFieldNodes.headOption.map(fieldNode=>(fieldNode\"value").text)
  }

  def extractProjectList(metaNode:Node):List[String] = {
    val projectIdNodes = (metaNode \ "timespan" \ "field").filter(fieldNode=>(fieldNode\"name").text=="__child_collection")
    projectIdNodes.map(node=>(node\"value").text).toList
  }

  def fromXml(collection:Node):Try[PlutoCommission] = Try {
    PlutoCommission(
      (collection \ "id").text,
      (collection \ "name").text,
      extractProjectList((collection \ "metadata").head),
      extractMetadataValue((collection \ "metadata").head, "gnm_commission_status").getOrElse("unknown"),
      extractMetadataValue((collection \ "metadata").head, "gnm_commission_scheduledcompletion").map(LocalDate.parse)
    )
  }
}
