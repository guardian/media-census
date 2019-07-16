package mfmodels

import vidispine.{FieldNames, VSLazyItem}

/**
  * ["gnm_external_archive_external_archive_request",
  * "gnm_external_archive_external_archive_status",
  * "gnm_external_archive_external_archive_device",
  * "gnm_external_archive_external_archive_path",
  * "gnm_external_archive_committed_to_archive_at",
  * "gnm_external_archive_last_restore_operation",
  * "gnm_external_archive_external_archive_report",
  * "gnm_external_archive_delete_shape"]
  */
case class ExternalArchiveData(archiveRequest:Option[String],archiveStatus:Option[String],archiveDevice:Option[String],archivePath:Option[String])

object ExternalArchiveData extends ((Option[String],Option[String],Option[String],Option[String])=>ExternalArchiveData) {
  def fromLazyItem(item:VSLazyItem):ExternalArchiveData = {
    new ExternalArchiveData(
      item.get(FieldNames.EXTERNAL_ARCHIVE_REQUEST).flatMap(_.headOption),
      item.get(FieldNames.EXTERNAL_ARCHIVE_STATUS).flatMap(_.headOption),
      item.get(FieldNames.EXTERNAL_ARCHIVE_DEVICE).flatMap(_.headOption),
      item.get(FieldNames.EXTERNAL_ARCHIVE_PATH).flatMap(_.headOption),
    )
  }
}