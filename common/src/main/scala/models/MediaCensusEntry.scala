package models

case class MediaCensusEntry (originalSource:AssetSweeperFile, sourceStorage:Option[String], storageSubpath:Option[String], vsFileId:Option[String], vsItemId:Option[String], replicas:Seq[VSFileLocation])
