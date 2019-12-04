package vidispine

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

case class ArchivalMetadata(committedAt:Option[ZonedDateTime], externalArchiveDevice:Option[String], externalArchiveRequest:Option[String],
                            externalArchiveReport:Option[String],externalArchiveStatus:Option[String],externalArchivePath:Option[String],
                            externalArchiveDeleteShape:Option[Boolean],externalArchiveProblems:Option[String]) {
  import ArchivalMetadata._

  def makeXmlBlock(key:String, value:String) = <field>
    <name>{key}</name>
    <value>{value}</value>
  </field>

  /**
    * make an XML document for the given metadata
    * @return
    */
  def makeXml:NodeSeq  = {
    <group>
      <name>ExternalArchiveRequest</name>
      {committedAt.map(value=>makeXmlBlock("gnm_external_archive_committed_to_archive_at",value.format(ArchivalMetadata.vidispineDateFormat))).getOrElse(NodeSeq.Empty)}
      {externalArchiveDevice.map(value=>makeXmlBlock("gnm_external_archive_external_archive_device", value)).getOrElse(NodeSeq.Empty)}
      {externalArchiveRequest.map(value=>makeXmlBlock("gnm_external_archive_external_archive_request",value)).getOrElse(NodeSeq.Empty)}
      {externalArchiveReport.map(value=>makeXmlBlock("gnm_external_archive_external_archive_report", value)).getOrElse(NodeSeq.Empty)}
      {externalArchiveStatus.map(value=>makeXmlBlock("gnm_external_archive_external_archive_status", value)).getOrElse(NodeSeq.Empty)}
      {externalArchivePath.map(value=>makeXmlBlock("gnm_external_archive_external_archive_path", value)).getOrElse(NodeSeq.Empty)}
      {externalArchiveDeleteShape.map(deleteValue=>
        <field>
          <name>gnm_external_archive_delete_shape</name>
          <value>{if(deleteValue) "true" else "false"}</value>
        </field>
      ).getOrElse(NodeSeq.Empty)}
      {externalArchiveProblems.map(probsValue=>
        <field>
          <name>gnm_external_archive_problems</name>
          <value>{probsValue}</value>
        </field>
      ).getOrElse(NodeSeq.Empty)}
    </group>
  }

  /**
    * check that the status field is set to an acceptable value
    * @return a Boolean, true if it's ok
    */
  def validateStatus: Boolean =
    externalArchiveStatus==Some(AS_NONE) || externalArchiveStatus==Some(AS_RUNNING) || externalArchiveStatus==Some(AS_FAILED) ||
    externalArchiveStatus==Some(AS_VERIFYING) || externalArchiveStatus==Some(AS_ARCHIVED)

  def validateRequest:Boolean =
    externalArchiveRequest==Some(AR_NONE) || externalArchiveRequest==Some(AR_REQUESTED) || externalArchiveRequest==Some(AR_REQUESTEDRESTORE)

}

object ArchivalMetadata {
  val interestingFields = Seq(
    "gnm_external_archive_committed_to_archive_at",
    "gnm_external_archive_external_archive_device",
    "gnm_external_archive_external_archive_request",
    "gnm_external_archive_external_archive_report",
    "gnm_external_archive_external_archive_status",
    "gnm_external_archive_external_archive_path",
    "gnm_external_archive_delete_shape",
    "gnm_external_archive_problems"
  )

  //ExternalArchiveRequest values
  val AR_NONE = "None"
  val AR_REQUESTED = "Requested Archive"
  val AR_REQUESTEDRESTORE = "Requested Restore"

  //ExternalArchiveStatus values
  val AS_NONE = "Not In External Archive"
  val AS_RUNNING = "Upload in Progress"
  val AS_FAILED = "Upload Failed"
  val AS_VERIFYING = "Awaiting Verification"
  val AS_ARCHIVED = "Archived"

  val vidispineDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")

  private def boolFromString(str: String): Boolean = {
    val toCheck = str.toLowerCase
    if(toCheck=="true") true else false
  }

  /**
    * fetch the required fields onto the given VSLazyItem.  Use this if you want to keep the updated item around
    * @param initialItem
    * @param comm
    * @param mat
    * @return
    */
  def fetchForItem(initialItem:VSLazyItem)(implicit comm:VSCommunicator, mat:Materializer) =
    initialItem.getMoreMetadata(interestingFields)

  def dateTimeMaybeZoned(timeString: String):ZonedDateTime = {
    Try {
      ZonedDateTime.parse(timeString,DateTimeFormatter.ISO_DATE_TIME)
    } match {
      case Success(dateTime)=>dateTime
      case Failure(err:java.time.DateTimeException)=>
        Try {
          val localTime = LocalDateTime.parse(timeString, DateTimeFormatter.ISO_DATE_TIME)
          localTime.atZone(ZoneId.systemDefault())
        } match {
          case Success(dateTime)=>dateTime
          case Failure(err)=>throw err
        }
      case Failure(otherError)=>throw otherError
    }
  }
  /**
    * returns ArchivalMetadata for the given VSLazyItem. Can optionally ensure that data is fetched beforehand; if you don't care
    * about keeping the updated VSLazyItem around then call this with alwaysFetch=true. Otherwise, call `fetchForItem` first and then
    * pass the provided lazyItem onto this method
    * @param initialItem item to convert
    * @param alwaysFetch if true, call out to VS to fetch required fields. If false, assume that they are present
    * @param comm implicitly provided VSCommunicator
    * @param mat implicitly provided Materializer
    * @param ec implicitly provided ExecutionContext
    * @return a Future with either a GetMetadataError or ArchivalMetadata
    */
  def fromLazyItem(initialItem:VSLazyItem, alwaysFetch:Boolean=false)(implicit comm:VSCommunicator, mat:Materializer, ec:ExecutionContext) = {
    val maybeActualItem = if(alwaysFetch){
      fetchForItem(initialItem)
    } else {
      Future(Right(initialItem))
    }

    maybeActualItem.map(_.map(item=>new ArchivalMetadata(
      item.getSingle("gnm_external_archive_committed_to_archive_at").map(dateTimeMaybeZoned),
      item.getSingle("gnm_external_archive_external_archive_device"),
      item.getSingle("gnm_external_archive_external_archive_request"),
      item.getSingle("gnm_external_archive_external_archive_report"),
      item.getSingle("gnm_external_archive_external_archive_status"),
      item.getSingle("gnm_external_archive_external_archive_path"),
      item.getSingle("gnm_external_archive_delete_shape").map(boolFromString),
      item.getSingle("gnm_external_archive_problems")
    )))
  }
}