package vidispine

import akka.stream.Materializer
import models.HttpError
import org.slf4j.LoggerFactory
import vidispine.VSCommunicator.OperationType
import vidispine.VSLazyItem.logger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq, XML}

case class GetMetadataError(httpError:Option[HttpError], vsError:Option[VSError], xmlError:Option[String])

case class VSLazyItem (itemId:String, lookedUpMetadata:Map[String,VSMetadataEntry]=Map(), shapes:Option[Map[String,VSShape]]=None) {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * looks up the given field list and returns a new object with their values set in `lookedUpMetadata`.
    * since this goes over the network, it is a future and may fail; a Left is returned in this case.
    * @param fieldList metadata fields to look up
    * @param comm implicitly provided VSCommunicator object
    * @param mat implicitly provided akka Materializer
    * @return
    */
  def getMoreMetadata(fieldList:Seq[String])(implicit comm:VSCommunicator, mat:Materializer):Future[Either[GetMetadataError,VSLazyItem]] =
    comm.request(OperationType.GET, s"/API/item/$itemId/metadata", None, Map("Accept"->"application/xml"), Map("field"->fieldList.mkString(","))).map({
      case Right(returnedXml) =>
        logger.debug(s"Vidispine returned $returnedXml")
        val maybeParsedData = Try {
          XML.loadString(returnedXml)
        }
        maybeParsedData match {
          case Success(parsedData) =>
            val newMetadataSeq = VSMetadataEntry.fromXml(parsedData \ "item" \ "metadata" \ "timespan")
            logger.debug(s"Got ${newMetadataSeq.length} more metadata keys")

            val newMetadataMap = newMetadataSeq
              .map(entry => entry.name -> entry)
              .foldLeft(Map[String,VSMetadataEntry]())((acc, elem)=>{
                val updatedMetaEntry = if(acc.keySet.contains(elem._1)) {
                  acc(elem._1).mergeValues(elem._2)
                } else {
                  elem._2
                }
                acc ++ Map(elem._1 -> updatedMetaEntry)
              })
            Right(this.copy(lookedUpMetadata = this.lookedUpMetadata ++ newMetadataMap))
          case Failure(parseError) =>
            logger.error(s"Could not parse XML returned from Vidispine: ", parseError)
            Left(GetMetadataError(None, None, Some(parseError.toString)))
        }
      case Left(httpErr) =>
        logger.debug(s"Vidispine returned an error ${httpErr.toString}")
        Left(GetMetadataError(Some(httpErr), None, None))
    })

  /**
    * get any metadata for the given key
    */
  def get(fieldName:String):Option[Seq[String]] = {
    lookedUpMetadata.get(fieldName).map(_.values.map(_.value))
  }

  def getSingle(fieldName:String):Option[String] = {
    get(fieldName).flatMap(_.headOption)
  }

  def toXmlDoc(rootGroup:Option[String]):Node = {
    <MetadataDocument xmlns="http://xml.vidispine.com/schema/vidispine">
      lookedUpMetadata.map(mdTuple=>{
      <field name="${mdTuple._1}">
        mdTuple._2.toSimpleXml()
      </field>
      }
    </MetadataDocument>
  }
}

object VSLazyItem extends ((String,Map[String,VSMetadataEntry],Option[Map[String,VSShape]])=>VSLazyItem) {
  /**
    * simple constructor for blank item
    * @param itemId item id to hold
    * @return VSLazyItem with nothing loaded
    */
  def apply(itemId:String) = new VSLazyItem(itemId,Map(),None)

  /**
    * construct from a single <item> stanza in an ItemListDocument
    * @param xml parsed xml node pointing to an <item>
    */
  def fromXmlSearchStanza(xml:Node) = {
    val itemId = xml \@ "id"

    val metadataSeq = VSMetadataEntry.fromXml(xml \ "metadata" \ "timespan")
    val newMetadataMap = metadataSeq.map(entry => entry.name -> entry).toMap
    val shapes = (xml \ "shape").map(VSShape.fromXml)
    val shapesMap = shapes.map(entry=>entry.tag->entry).toMap

    new VSLazyItem(itemId,newMetadataMap,if(shapesMap.isEmpty) None else Some(shapesMap))
  }
}