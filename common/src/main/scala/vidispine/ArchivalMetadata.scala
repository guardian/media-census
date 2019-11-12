package vidispine

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

case class ArchivalMetadata(committedAt:ZonedDateTime, externalArchiveDevice:String, externalArchiveRequest:String,
                            externalArchiveReport:String,externalArchiveStatus:String,externalArchivePath:String,
                            externalArchiveDeleteShape:Boolean,externalArchiveProblems:Option[String]) {
  import ArchivalMetadata._
  /**
    * make an XML document for the given metadata
    * @return
    */
  def makeXml:NodeSeq  = {
    <group name="ExternalArchiveRequest">
      <field name="gnm_external_archive_committed_to_archive_at">
        <value>${committedAt.format(ArchivalMetadata.vidispineDateFormat)}</value>
      </field>
      <field name="gnm_external_archive_external_archive_device">
        <value>${externalArchiveDevice}</value>
      </field>
      <field name="gnm_external_archive_external_archive_request">
        <value>${externalArchiveRequest}</value>
      </field>
      <field name="gnm_external_archive_external_archive_report">
        <value>${externalArchiveReport}</value>
      </field>
      <field name="gnm_external_archive_external_archive_status">
        <value>${externalArchiveStatus}</value>
      </field>
      <field name="gnm_external_archive_external_archive_path">
        <value>${externalArchivePath}</value>
      </field>
      <field name="gnm_external_archive_delete_shape">
        <value>${if(externalArchiveDeleteShape) "true" else "false"}</value>
      </field>
      ${externalArchiveProblems.map(probsValue=>
        <field name="gnm_external_archive_problems">
          <value>${probsValue}</value>
        </field>
    )}
    </group>
  }

  /**
    * check that the status field is set to an acceptable value
    * @return a Boolean, true if it's ok
    */
  def validateStatus: Boolean =
    externalArchiveStatus==AS_NONE || externalArchiveStatus==AS_RUNNING || externalArchiveStatus==AS_FAILED ||
    externalArchiveStatus==AS_VERIFYING || externalArchiveStatus==AS_ARCHIVED

  def validateRequest:Boolean =
    externalArchiveRequest==AR_NONE || externalArchiveRequest==AR_REQUESTED || externalArchiveRequest==AR_REQUESTEDRESTORE

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

  val vidispineDateFormat = DateTimeFormatter.ISO_DATE_TIME

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
      ZonedDateTime.parse(item.getSingle("gnm_external_archive_committed_to_archive_at").get),
      item.getSingle("gnm_external_archive_external_archive_device").get,
      item.getSingle("gnm_external_archive_external_archive_request").get,
      item.getSingle("gnm_external_archive_external_archive_report").get,
      item.getSingle("gnm_external_archive_external_archive_status").get,
      item.getSingle("gnm_external_archive_external_archive_path").get,
      boolFromString(item.getSingle("gnm_external_archive_delete_shape").get),
      item.getSingle("gnm_external_archive_problems")
    )))
  }
}