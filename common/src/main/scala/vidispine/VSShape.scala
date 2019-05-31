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
  def vsIdForShapeTag(itemId:String, shapeTag:String)(implicit vsCommunicator:VSCommunicator,mat:Materializer,logger:DiagnosticLoggingAdapter) = {
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

  def forItem(itemId:String, shapeTag:String)(implicit vsCommunicator:VSCommunicator,mat:Materializer,logger:DiagnosticLoggingAdapter) = {
    val maybeUriFuture = vsIdForShapeTag(itemId, shapeTag).map({
      case Left(err)=>Left(err)
      case Right(None)=>Left(HttpError(s"No shape exists for tag $shapeTag", 404))
      case Right(Some(shapeId))=>Right(s"/API/item/$itemId/shape/$shapeId")
    })

    maybeUriFuture.flatMap({
      case Right(uri)=>
        vsCommunicator.requestGet(uri, Map("Accept" -> "application/xml"))
          .map({
            case Right(stringData) => VSShape.fromXml(stringData) match {
              case Success(vsShape) =>
                Right(vsShape)
              case Failure(err) =>
                Left(HttpError(err.toString, -1))
            }
            case Left(err) => Left(err)
          })
      case Left(err)=>Future(Left(err))
    })
  }

  def fromXml(xmlString:String):Try[VSShape] = Try {
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
