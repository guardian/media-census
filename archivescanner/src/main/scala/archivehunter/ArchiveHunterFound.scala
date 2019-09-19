package archivehunter

trait ArchiveHunterLookupResult

case class ArchiveHunterFound(archiveHunterId:String, archiveHunterCollection:String, beenDeleted:Boolean) extends ArchiveHunterLookupResult
case object ArchiveHunterNotFound extends ArchiveHunterLookupResult
