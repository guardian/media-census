import java.time.ZonedDateTime
import java.util.UUID

import com.sksamuel.elastic4s.http.ElasticError
import com.sksamuel.elastic4s.http.delete.DeleteResponse
import helpers.CleanoutFunctions
import models.{JobHistory, JobHistoryDAO, JobType}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import testhelpers.AkkaTestkitSpecs2Support

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class CleanoutFunctionsSpec extends Specification with Mockito {
  "CleanoutFunctions.cleanoutOldJobs" should {
    "request a delete on any returned jobs that are older than the given threshold" in {
      class ToTest extends CleanoutFunctions {}

      val sampleList = IndexedSeq(
        JobHistory(UUID.fromString("A330F437-0206-4A4D-8826-4C1C78FBBEF9"),Some(JobType.CensusScan),ZonedDateTime.now().minusDays(3L),None,None,0,1,2,3,4,5),
        JobHistory(UUID.fromString("A5B93958-2D04-4952-8672-AE3E970BFC60"),Some(JobType.CensusScan),ZonedDateTime.now().minusDays(2L),None,None,0,1,2,3,4,5),
        JobHistory(UUID.fromString("B5B2077E-D71A-4E80-ADD1-74E8A689897C"),Some(JobType.CensusScan),ZonedDateTime.now().minusDays(1L),None,None,0,1,2,3,4,5),
      )
      Thread.sleep(1000)  //prevent test weirdness due to being exactly  on/1 second off ZonedDateTime.now() in the routine under test.

      val mockedDAO = mock[JobHistoryDAO]
      mockedDAO.queryJobs(any,any,any) returns Future(Right(sampleList))
      mockedDAO.delete(any) returns Future(Right(mock[DeleteResponse]))

      val toTest = new ToTest

      val result = Await.result(toTest.cleanoutOldJobs(mockedDAO,JobType.CensusScan,2L), 30 seconds)

      there was one(mockedDAO).queryJobs(Some(JobType.CensusScan),Some(JobHistoryDAO.JobState.Running),None)

      got {
        one(mockedDAO).delete(UUID.fromString("A330F437-0206-4A4D-8826-4C1C78FBBEF9"))
      }

      there was no(mockedDAO).delete(UUID.fromString("B5B2077E-D71A-4E80-ADD1-74E8A689897C"))
      result must beRight
      result.right.get.seq.length mustEqual 3
    }

    "continue deleting on error" in {
      class ToTest extends CleanoutFunctions {}

      val sampleList = IndexedSeq(
        JobHistory(UUID.fromString("A330F437-0206-4A4D-8826-4C1C78FBBEF9"),Some(JobType.CensusScan),ZonedDateTime.now().minusDays(3L),None,None,0,1,2,3,4,5),
        JobHistory(UUID.fromString("A5B93958-2D04-4952-8672-AE3E970BFC60"),Some(JobType.CensusScan),ZonedDateTime.now().minusDays(2L),None,None,0,1,2,3,4,5),
        JobHistory(UUID.fromString("B5B2077E-D71A-4E80-ADD1-74E8A689897C"),Some(JobType.CensusScan),ZonedDateTime.now().minusDays(1L),None,None,0,1,2,3,4,5),
      )
      Thread.sleep(1000)  //prevent test weirdness due to being exactly  on/1 second off ZonedDateTime.now() in the routine under test.

      val mockedDAO = mock[JobHistoryDAO]
      mockedDAO.queryJobs(any,any,any) returns Future(Right(sampleList))
      mockedDAO.delete(any) returns Future(Left(mock[ElasticError])) thenReturn Future(Right(mock[DeleteResponse]))

      val toTest = new ToTest

      val result = Await.result(toTest.cleanoutOldJobs(mockedDAO,JobType.CensusScan,1L), 30 seconds)

      there was one(mockedDAO).queryJobs(Some(JobType.CensusScan),Some(JobHistoryDAO.JobState.Running),None)

      got {
        one(mockedDAO).delete(UUID.fromString("A330F437-0206-4A4D-8826-4C1C78FBBEF9"))
        one(mockedDAO).delete(UUID.fromString("A5B93958-2D04-4952-8672-AE3E970BFC60"))
      }

      result must beLeft
      result.left.get.seq.length mustEqual 1
    }
  }

  "return a search error as a Left" in {
    class ToTest extends CleanoutFunctions {}

    val mockedDAO = mock[JobHistoryDAO]
    mockedDAO.queryJobs(any,any,any) returns Future(Left(mock[ElasticError]))
    mockedDAO.delete(any) returns Future(Right(mock[DeleteResponse])) thenReturn Future(Right(mock[DeleteResponse]))

    val toTest = new ToTest

    val result = Await.result(toTest.cleanoutOldJobs(mockedDAO,JobType.CensusScan,1L), 30 seconds)

    there was one(mockedDAO).queryJobs(Some(JobType.CensusScan),Some(JobHistoryDAO.JobState.Running),None)
    there were no(mockedDAO).delete(any)

    result must beLeft
    result.left.get.seq.length mustEqual 1
  }
}
