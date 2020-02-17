package models

import com.amazonaws.services.s3.model.ObjectMetadata
import vidispine.{ArchivalMetadata, VSFile}

case class ArchivedItemRecord (nearlineItem:VSFile, s3Bucket:String, s3Path:String, s3Meta:Option[ObjectMetadata]=None, vsMeta:Option[ArchivalMetadata]=None)
