package models

object MediaStatusValue extends Enumeration {
  val SHOULD_BE_ARCHIVED,NO_PROJECT,BRANDING,DELETABLE,SENSITIVE,PROJECT_HELD,PROJECT_OPEN,UNKNOWN = Value
  //PROJECT_OPEN_COMMISSION_COMPLETED,PROJECT_OPEN_COMMISSION_EXPIRED,PROJECT_OPEN_COMMISSION_OPEN

}

case class UnclogOutput (VSFileId:String, VSItemId:String, FileSize:Long, ParentCollectionIds:Seq[String], MediaStatus:MediaStatusValue.Value)