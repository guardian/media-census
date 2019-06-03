package vidispine

import java.time.ZonedDateTime

import akka.stream.Materializer
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}
import scala.xml.{NodeSeq, XML}
import scala.concurrent.ExecutionContext.Implicits.global

case class VSFile(vsid:String, path:String, uri:String, state:Option[VSFileState.Value], size:Long, hash:Option[String], timestamp:ZonedDateTime,refreshFlag:Int,storage:String, metadata:Option[Map[String,String]], membership: Option[VSFileItemMembership])

object VSFile {
  val logger = LoggerFactory.getLogger(getClass)
  def metadataDictFromNodes(metadataNode:NodeSeq):Try[Map[String,String]] = Try {
    (metadataNode \ "field").map(fieldNode=>
      ((fieldNode \ "key").text, (fieldNode \ "value").text)
    ).toMap
  }

  def optionNullOrBlank(str:String):Option[String] = {
    if(str==null || str==""){
      None
    } else {
      Some(str)
    }
  }

  def fromXml(xmlNode:NodeSeq):Try[VSFile] = Try {
    new VSFile(
      (xmlNode \ "id").text,
      (xmlNode \ "path").text,
      (xmlNode \ "uri").text,
      optionNullOrBlank((xmlNode \ "state").text).map(value=>VSFileState.withName(value)),
      (xmlNode \ "size").text.toLong,
      Option((xmlNode \ "hash").text),
      ZonedDateTime.parse((xmlNode \ "timestamp").text),
      (xmlNode \ "refreshFlag").text.toInt,
      (xmlNode \ "storage").text,
      metadataDictFromNodes(xmlNode \ "metadata") match {
        case Success(mdDict)=>Some(mdDict)
        case Failure(err)=>None
      },
      (xmlNode \ "item").headOption.map(node=>VSFileItemMembership.fromXml(node) match {
        case Success(membership)=>membership
        case Failure(err)=>throw err
      })
    )
  }

  /**
    * convert the absolute path of a file to a relative path on the storage
    * @param fullPath path of the item on-disk
    * @param storageRoot root path of the storage
    * @return either the relative path or None if the paths don't overlap
    */
  def storageSubpath(fullPath:String, storageRoot:String) =
    if(!fullPath.startsWith(storageRoot)){
      None
    } else {
      val maybePath = fullPath.substring(storageRoot.length)
      if (maybePath.startsWith("/")) {
        Some(maybePath.substring(1))
      } else {
        Some(maybePath)
      }
    }


  def fromXmlString(str:String) = {
    val xmlNodes = XML.loadString(str)
    if((xmlNodes \ "file").nonEmpty){
      VSFile.fromXml(xmlNodes \ "file") //if retrieving the file directly, you get this extra node layer
    } else {
      VSFile.fromXml(xmlNodes)
    }
  }

  def forPathOnStorage(storageId:String, path:String)(implicit communicator: VSCommunicator, mat:Materializer) = {
    val uri = s"/API/storage/$storageId/file;includeItem=true"
    communicator.requestGet(uri, Map("Accept"->"application/xml"), queryParams = Map("path"->path,"count"->"false")).map({
      case Left(err)=>Left(err.toString)
      case Right(xmlString)=>VSFile.fromXmlString(xmlString) match {
        case Failure(err)=>
          err.printStackTrace()
          logger.error(xmlString)
          Left(err.toString)
        case Success(vsFile)=>Right(vsFile)
      }
    })
  }
}