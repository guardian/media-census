package vidispine

import akka.event.DiagnosticLoggingAdapter
import akka.stream.Materializer
import com.softwaremill.sttp.Uri
import models.HttpError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml.XML

case class VSShape (vsid:String,essenceVersion:Int,tag:String,mimeType:String, files:Seq[VSFile])

object VSShape {
  def vsIdForShapeTag(itemId:String, shapeTag:String)(implicit vsCommunicator:VSCommunicator,mat:Materializer) = {
    val uri = s"/API/item/$itemId/shape?tag=$shapeTag"
    vsCommunicator.requestGet(uri,Map("Accept"->"application/xml"))
      .map({
        case Right(stringData)=>UriListDocument.fromXmlString(stringData) match {
          case Success(uriListDocument)=>
            Right(uriListDocument.uri.headOption)
          case Failure(err)=>
            Left(HttpError(err.toString, -1))
        }
        case Left(err)=>
          Left(err)
      })
  }

  def forItemWithId(itemId:String, shapeId:String)(implicit vsCommunicator:VSCommunicator,mat:Materializer) = {
    val uri = s"/API/item/$itemId/shape/$shapeId"
    vsCommunicator.requestGet(uri, Map("Accept" -> "application/xml"))
      .map({
        case Right(stringData) => VSShape.fromXmlString(stringData) match {
          case Success(vsShape) =>
            Right(vsShape)
          case Failure(err) =>
            Left(HttpError(err.toString, -1))
        }
        case Left(err) => Left(err)
      })
  }

  def forItemWithTag(itemId:String, shapeTag:String)(implicit vsCommunicator:VSCommunicator,mat:Materializer) = {
    vsIdForShapeTag(itemId, shapeTag).flatMap({
      case Left(err) => Future(Left(err))
      case Right(None) => Future(Left(HttpError(s"No shape exists for tag $shapeTag", 404)))
      case Right(Some(shapeId)) => forItemWithId(itemId, shapeId)
    })
  }


  def fromXmlString(xmlString:String):Try[VSShape] = Try {
    val xmlNodes = XML.loadString(xmlString)

    new VSShape(
      (xmlNodes \ "id").text,
      (xmlNodes \ "essenceVersion").text.toInt,
      (xmlNodes \ "tag").text,
      (xmlNodes \ "mimeType").text,
      (xmlNodes \ "containerComponent" \ "file").map(fileNode=>VSFile.fromXml(fileNode) match {
        case Success(vsFile)=>vsFile
        case Failure(err)=>throw err  //this is picked up immediately by the containing Try and returned as a Failure
      })
    )
  }
}
