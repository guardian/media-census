package vidispine

import akka.stream.Materializer
import models.HttpError
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.XML

case class VSLazyItem (itemId:String, lookedUpMetadata:Map[String,VSMetadataEntry]=Map()) {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * looks up the given field list and returns a new object with their values set in `lookedUpMetadata`.
    * since this goes over the network, it is a future and may fail; a Left is returned in this case.
    * @param fieldList metadata fields to look up
    * @param comm implicitly provided VSCommunicator object
    * @param mat implicitly provided akka Materializer
    * @return
    */
  def getMoreMetadata(fieldList:Seq[String])(implicit comm:VSCommunicator, mat:Materializer):Future[Either[HttpError,VSLazyItem]] = {
    comm.requestGet(s"/API/item/$itemId/metadata?field=${fieldList.mkString(",")}", Map("Accept"->"application/xml")).map(_.map(returnedXml=>{
      val parsedData = XML.loadString(returnedXml)
      val newMetadataSeq = VSMetadataEntry.fromXml(parsedData \ "item" \ "metadata" \ "timespan")
      logger.debug(s"Got ${newMetadataSeq.length} more metadata keys")

      val newMetadataMap = newMetadataSeq.map(entry=>entry.name->entry).toMap
      this.copy(lookedUpMetadata = this.lookedUpMetadata ++ newMetadataMap)
    }))
  }

  /**
    * get any metadata for the given key
    */
  def get(fieldName:String):Option[Seq[String]] = {
    lookedUpMetadata.get(fieldName).map(_.values.map(_.value))
  }

  def getSingle(fieldName:String):Option[String] = {
    get(fieldName).flatMap(_.headOption)
  }
}