package helpers

import java.time.ZonedDateTime

import models.{JobHistoryDAO, JobType}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait CleanoutFunctions {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * Purge out any uncompleted jobs that are older than `leaveOpenDays` days
    * @param jobHistoryDAO DAO for Job History
    * @param jobType JobType value indicating the type of job to check
    * @param leaveOpenDays delete open jobs that are older than this length of time
    * @return a Future, with a sequence containing the result of each delete operation - Left for an error, or Right for success/not needed
    */
  def cleanoutOldJobs(jobHistoryDAO:JobHistoryDAO, jobType: JobType.Value, leaveOpenDays:Long) = {
    jobHistoryDAO.queryJobs(Some(jobType),Some(JobHistoryDAO.JobState.Running),None).flatMap({
      case Left(err)=>
        logger.error(s"Could not clear out old jobs: $err")
        Future(Left(Seq(err)))
      case Right(results)=>
        val futuresList = results.map(entry=>{
          val age = ZonedDateTime.now().toInstant.getEpochSecond - entry.scanStart.toInstant.getEpochSecond
          logger.info(s"Got open job ${entry.jobId} which is ${age/3600} hours old")
          val maxAgeSeconds = leaveOpenDays*3600*24
          if(age>maxAgeSeconds){
            jobHistoryDAO.delete(entry.jobId).map(_.map(_.result))
          } else {
            Future(Right(s"Not deleted, age was ${age/(3600*24)} days"))
          }
        })
        Future.sequence(futuresList).map(results=>{
          val failures = results.collect({case Left(err)=>err})
          if(failures.nonEmpty){
            Left(failures)
          } else {
            Right(results.collect({case Right(result)=>result}))
          }
        })
    })
  }

}
