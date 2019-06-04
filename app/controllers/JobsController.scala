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
import org.apache.xerces.xs.datatypes.ObjectList

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

@Singleton
class JobsController @Inject() (config:Configuration, jobsModelDAOinj:InjectableJobsModelDAO, cc:ControllerComponents)
  extends AbstractController(cc) with Circe with ZonedDateTimeEncoder {
  private val jobsModelDAO = jobsModelDAOinj.dao
  private val logger = LoggerFactory.getLogger(getClass)

  def jobsForTimespan(start:Option[String], finish:Option[String]) = Action.async{
    try {
      val maybeStartTime = start.map(startTimeString => ZonedDateTime.parse(startTimeString))

      val finishTime = finish match {
        case None=>ZonedDateTime.now()
        case Some(finishTimeString)=>ZonedDateTime.parse(finishTimeString)
      }

      jobsModelDAO.jobsForTimespan(maybeStartTime, finishTime).map({
        case Left(err)=>
          logger.error(s"Elasticsearch lookup failed: $err")
          InternalServerError(GenericResponse("db_error", err.toString).asJson)
        case Right(jobsList)=>
          //FIXME: should really return "actual" count from DAO and pass it back here
          Ok(ObjectListResponse("ok","jobsHistory", jobsList, jobsList.length).asJson)
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

  def runningJobs = Action.async {
    jobsModelDAO.runningJobs.map({
      case Left(err)=>
        logger.error(s"Could not list currently running jobs: $err")
        InternalServerError(GenericResponse("db_error", err.toString).asJson)
      case Right(resultSeq)=>
        Ok(ObjectListResponse("ok","jobHistory", resultSeq, resultSeq.length).asJson)
    })
  }
}
