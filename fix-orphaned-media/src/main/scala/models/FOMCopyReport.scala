package models

import vidispine.VSFile

object CopyStatus extends Enumeration {
  val EXISTS_ALREADY,SOURCE_NOT_FOUND,SUCCESS,FAILURE = Value
}

case class S3Destination(bucketName:String, key:String)

case class FOMCopyReport(source:VSFile,dest:S3Destination, status:CopyStatus.Value,eTag:Option[String]=None,errorMessage:Option[String]=None)
