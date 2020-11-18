package utils

import akka.stream.Materializer
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSLazyItem}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.{NodeSeq, XML}

case class ItemProject (title:String, status:String, sensitive:Boolean, deepArchive:Boolean, deletable:Boolean)

object ItemProject {
  private val logger = LoggerFactory.getLogger(getClass)

  def findField(fields:NodeSeq, target:String, separator:String=" "):Option[String] = {
    val values = for {
      field <- fields
      values <- (field \ "value") if ((field \ "name").text==target)
    } yield values.text
    if(values.isEmpty) {
      None
    } else {
      Some(values.mkString(separator))
    }
  }

  def fieldAsBool(fields:NodeSeq, target:String) = {
    findField(fields, target).isDefined
  }

  def forCollection(collectionId:String)(implicit vscomm:VSCommunicator, mat:Materializer) = {
    vscomm.request(VSCommunicator.OperationType.GET, s"/API/collection/$collectionId/metadata",None, Map()).map({
      case Left(httpError)=>
        logger.error(s"Could not get metadata for collection $collectionId: ", httpError)
        throw new RuntimeException("Could not get metadata for collection")
      case Right(xmlString)=>
        val xmlContent = XML.loadString(xmlString)
        val fields = xmlContent \ "timespan" \ "field"
        new ItemProject(
          findField(fields, "gnm_project_title").getOrElse("no title"),
          findField(fields, "gnm_project_status").getOrElse("Completed"),
          fieldAsBool(fields, "gnm_storage_rule_sensitive"),
          fieldAsBool(fields, "gnm_storage_rule_deep_archive"),
          fieldAsBool(fields, "gnm_storage_rule_deletable")
        )
    })
  }
}
