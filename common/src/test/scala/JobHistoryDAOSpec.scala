import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import models.{JobHistory, JobHistoryDAO, JobType}
import org.elasticsearch.client.ElasticsearchClient
import com.sksamuel.elastic4s.testkit.ClientProvider
import org.specs2.mutable.Specification

import scala.concurrent.Await
import scala.concurrent.duration._

class JobHistoryDAOSpec extends Specification {
  "JobHistoryDAO.updateStatusOnly" should {
    "update the specified document without overwriting the counters" in {
      if(System.getProperty("have-local-elasticsearch")!=null) {
        val client = ElasticClient(ElasticProperties("http://localhost:9200"))

        val fakeStartTime = ZonedDateTime.of(2019, 1, 1, 23, 22, 21, 0, ZoneId.systemDefault())
        val fakeEndTime = ZonedDateTime.of(2019, 1, 2, 23, 22, 21, 0, ZoneId.systemDefault())
        val jobId = UUID.randomUUID()
        val initialDoc = JobHistory(jobId, Some(JobType.CensusScan), fakeStartTime, None, None, 1, 2, 3, 4, 5, 6)

        val daoToTest = new JobHistoryDAO(client, "test-jobs-index")

        Await.ready(daoToTest.put(initialDoc), 30 seconds)

        val statusUpdate = JobHistory(jobId, Some(JobType.CensusScan), initialDoc.scanStart, Some(fakeEndTime), Some("something went kaboom"), 0, 0, 0, 0, 0, 0)

        val result = Await.result(daoToTest.updateStatusOnly(statusUpdate), 30 seconds)
        result must beRight

        val actualRecord = Await.result(daoToTest.jobForUuid(jobId), 30 seconds)

        actualRecord must beRight
        actualRecord.right.get must beSome

        val output = actualRecord.right.get.get

        output.lastError must beSome("something went kaboom")
        output.scanStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) mustEqual fakeStartTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        output.scanFinish.get.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) mustEqual fakeEndTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        output.noBackupsCount mustEqual 1
        output.partialBackupsCount mustEqual 2
        output.fullBackupsCount mustEqual 3
        output.unimportedCount mustEqual 4
        output.unattachedCount mustEqual 5
        output.itemsCounted mustEqual 6
      } else {
        println("TEST SKIPPED - no elasticsearch present. Set -Dhave-local-elasticsearch to run the test, assuming you have ES at http://localhost:9200")
        1 mustEqual 1
      }

    }
  }
}
