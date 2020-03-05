package models

import java.time.LocalDate
import scala.util.Try
import scala.xml.Node

case class VidispineProject (vsid:String, name:Option[String], status:Option[String], commissionCollection:Option[String], deepArchive:Boolean, deletable:Boolean, sensitive:Boolean, created:Option[LocalDate], modified:Option[LocalDate])

object VidispineProject {
  def extractMetadataValue(metaNode:Node, fieldName:String):Option[String] = {
    val applicableFieldNodes = (metaNode \ "timespan" \ "field").filter(fieldNode=>(fieldNode\"name").text==fieldName)
    applicableFieldNodes.headOption.map(fieldNode=>(fieldNode\"value").text)
  }

  def checkField(field:Option[String], check:Option[String]): Boolean = {
    if (field == check)
      true
    else
      false
  }

  def fromXml(collection:Node):Try[VidispineProject] = Try {
    VidispineProject(
      (collection \ "id").text,
      Option((collection \ "name").text),
      extractMetadataValue((collection \ "metadata").head, "gnm_project_status"),
      extractMetadataValue((collection \ "metadata").head, "__parent_collection"),
      checkField(extractMetadataValue((collection \ "metadata").head, "gnm_storage_rule_deep_archive"), Option("storage_rule_deep_archive")),
      checkField(extractMetadataValue((collection \ "metadata").head, "gnm_storage_rule_deletable"), Option("storage_rule_deletable")),
      checkField(extractMetadataValue((collection \ "metadata").head, "gnm_storage_rule_sensitive"), Option("storage_rule_sensitive")),
      extractMetadataValue((collection \ "metadata").head, "created").map(LocalDate.parse),
      extractMetadataValue((collection \ "metadata").head, "__metadata_last_modified").map(LocalDate.parse)
    )
  }
}


