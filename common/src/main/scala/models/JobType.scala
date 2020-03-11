package models

import io.circe.{Decoder, Encoder}

object JobType extends Enumeration {
  type JobType = Value

  val CensusScan, DeletedScan, NearlineScan,ArchiveHunterScan,RemoveArchivedNearline,CommissionScan,UnclogScan = Value
}

trait JobTypeEncoder {
  implicit val jobTypeEncoder = Encoder.enumEncoder(JobType)
  implicit val jobTypeDecoder = Decoder.enumDecoder(JobType)
}