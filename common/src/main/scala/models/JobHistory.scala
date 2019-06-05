package models

import java.time.ZonedDateTime
import java.util.UUID

case class JobHistory (jobId:UUID, scanStart:ZonedDateTime, scanFinish:Option[ZonedDateTime], lastError:Option[String],
                       noBackupsCount:Int, partialBackupsCount:Int, fullBackupsCount:Int, unimportedCount:Long, unattachedCount:Long)

object JobHistory extends ((UUID, ZonedDateTime, Option[ZonedDateTime], Option[String], Int, Int, Int,Long,Long)=>JobHistory) {
  def newRun(startsAt:ZonedDateTime=ZonedDateTime.now()) = {
    new JobHistory(
      UUID.randomUUID(),
      startsAt,
      scanFinish = None,
      lastError = None,
      0,
      0,
      0,
      0,
      0
    )
  }
}