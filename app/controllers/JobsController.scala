package controllers

import java.time.ZonedDateTime
import java.util.UUID

import helpers.{InjectableJobsModelDAO, ZonedDateTimeEncoder}
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import responses.{GenericResponse, ObjectGetResponse, ObjectListResponse}
import io.circe.generic.auto._
import io.circe.syntax._
import models.{JobHistoryDAO, JobType, JobTypeEncoder}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

@Singleton
class JobsController @Inject() (config:Configuration, jobsModelDAOinj:InjectableJobsModelDAO, cc:ControllerComponents)
  extends AbstractController(cc) with Circe with ZonedDateTimeEncoder with JobTypeEncoder {
  private val jobsModelDAO = jobsModelDAOinj.dao
  private val logger = LoggerFactory.getLogger(getClass)

  def jobsForTimespan(jobType:String, start:Option[String], finish:Option[String],showRunning:Boolean) = Action.async{
    try {
      val maybeStartTime = start.map(startTimeString => ZonedDateTime.parse(startTimeString))

      val finishTime = finish match {
        case None=>ZonedDateTime.now()
        case Some(finishTimeString)=>ZonedDateTime.parse(finishTimeString)
      }

      val actualJobType = if(jobType=="all") None else Some(jobType)

      jobsModelDAO.jobsForTimespan(actualJobType, maybeStartTime, finishTime, showRunning).map({
        case Left(err)=>
          logger.error(s"Elasticsearch lookup failed: $err")
          InternalServerError(GenericResponse("db_error", err.toString).asJson)
        case Right((jobsList, totalHitCount))=>
          Ok(ObjectListResponse("ok","jobsHistory", jobsList, totalHitCount.toInt).asJson)
      })
    } catch {
      case err:Throwable=>
        logger.error(s"Could not look up jobs for timespan: ", err)
        Future(InternalServerError(GenericResponse("error", err.toString).asJson))
    }
  }


  def jobDetail(idString:String) = Action.async {
    val maybeUUID = Try { UUID.fromString(idString) }

    maybeUUID match {
      case Failure(err)=>
        Future(BadRequest(GenericResponse("bad_parameter", "You did not pass a valid UUID").asJson))
      case Success(uuid)=>
        jobsModelDAO.jobForUuid(uuid).map({
          case Left(err)=>
            logger.error(s"Could not look up job detail for $idString: $err")
            InternalServerError(GenericResponse("db_error", err.toString).asJson)
          case Right(None)=>
            NotFound(GenericResponse("not_found", idString).asJson)
          case Right(Some(result))=>
            Ok(ObjectGetResponse("ok","jobHistory", result).asJson)
        })
    }
  }

  def runningJobs(limit:Option[Int]) = Action.async {
    jobsModelDAO.queryJobs(None,Some(JobHistoryDAO.JobState.Running),limit).map({
      case Left(err)=>
        logger.error(s"Could not list currently running jobs: $err")
        InternalServerError(GenericResponse("db_error", err.toString).asJson)
      case Right(resultSeq)=>
        Ok(ObjectListResponse("ok","jobHistory", resultSeq, resultSeq.length).asJson)
    })
  }

  def lastSuccessfulJob(jobType:String, includeRunning: Boolean) = Action.async {
    Try { JobType.withName(jobType) } match {
      case Success(jobTypeValue) =>
        if(!includeRunning) {
          jobsModelDAO.queryJobs(Some(jobTypeValue), Some(JobHistoryDAO.JobState.Completed), Some(1)).map({
            case Left(err) =>
              logger.error(s"Could not find most recent job: $err")
              InternalServerError(GenericResponse("db_error", err.toString).asJson)
            case Right(resultSeq) =>
              //works better in the frontend to present as a 200 rather than a 404
              Ok(ObjectGetResponse("ok", "jobHistory", resultSeq.headOption).asJson)
          })
        } else {
          val queryFut = Future.sequence(Seq(
            jobsModelDAO.queryJobs(Some(jobTypeValue), Some(JobHistoryDAO.JobState.Completed), Some(1)),
            jobsModelDAO.queryJobs(Some(jobTypeValue), Some(JobHistoryDAO.JobState.Running), Some(1))
          ))

          queryFut.map(results => {
            val failures = results.collect({ case Left(err) => err })
            if (failures.nonEmpty) {
              logger.error(s"Could not get most recent job: $failures")
              InternalServerError(GenericResponse("db_error", failures.mkString(",")).asJson)
            } else {
              val success = results.collect({ case Right(result) => result.headOption })
              Ok(ObjectGetResponse("ok", "jobHistory", success).asJson)
            }
          })
        }
      case Failure(exception) =>
        logger.error(s"Could not convert $jobType into a JobType enum value", exception)
        Future(BadRequest(GenericResponse("bad_request", s"$jobType is not a valid job type").asJson))
    }
  }

  def manualDelete(idString:String) = Action.async {
    val maybeUuid = Try { UUID.fromString(idString) }

    maybeUuid match {
      case Failure(err)=>
        Future(BadRequest(GenericResponse("error","You must specify a valid UUID").asJson))
      case Success(uuid)=>
        jobsModelDAO.delete(uuid).map({
          case Left(err)=>
            logger.error(s"Could not delete job from index: $err")
            InternalServerError(GenericResponse("db_error", err.toString).asJson)
          case Right(_)=>
            Ok(GenericResponse("ok","job deleted").asJson)
        })
    }
  }
}
