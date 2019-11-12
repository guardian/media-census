package models

import vidispine.VSFile

case class ArchiveNearlineEntry (vsStorage: String, omUri:String, size:Long, archiveHunterId:Option[String], archiveHunterCollection:Option[String], archiveHunterDeleted:Option[Boolean])

object ArchiveNearlineEntry extends ((String,String,Long,Option[String],Option[String],Option[Boolean])=>ArchiveNearlineEntry) {

  /**
    * initialise a new entry from a VSFile, with archive hunter entries blank
    * @param vsFile
    * @return
    */
  def fromVSFileBlankArchivehunter(vsFile:VSFile) =
    new ArchiveNearlineEntry(vsFile.storage, vsFile.uri, vsFile.size, None,None, None)

}