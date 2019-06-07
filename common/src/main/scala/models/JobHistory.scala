package models

import java.time.ZonedDateTime
import java.util.UUID

case class JobHistory (jobId:UUID, jobType: Option[JobType.Value], scanStart:ZonedDateTime, scanFinish:Option[ZonedDateTime], lastError:Option[String],
                       noBackupsCount:Int, partialBackupsCount:Int, fullBackupsCount:Int, unimportedCount:Long, unattachedCount:Long, itemsCounted:Long)

object JobHistory extends ((UUID, Option[JobType.Value], ZonedDateTime, Option[ZonedDateTime], Option[String], Int, Int, Int,Long,Long,Long)=>JobHistory) {
  def newRun(`type`:JobType.Value, startsAt:ZonedDateTime=ZonedDateTime.now()) = {
    new JobHistory(
      UUID.randomUUID(),
      Some(`type`),
      startsAt,
      scanFinish = None,
      lastError = None,
      0,
      0,
      0,
      0,
      0,
      0
    )
  }
}