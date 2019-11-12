package archivehunter

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class ArchiveHunterRequestorSpec extends Specification with Mockito {
  "ArchiveHunterRequestor.makeAuth" should {
    "sign a pre-existing Request with current date and signature" in {
      val rq = new ArchiveHunterRequestor("https://archivehunter.company.org","secretkey")(mock[ActorSystem],mock[Materializer]) {
        override def currentTimeString: String = ZonedDateTime.parse("2019-04-05T01:02:03Z").format(DateTimeFormatter.RFC_1123_DATE_TIME)
      }

      val initialRequest = HttpRequest()
      val result = rq.makeAuth(initialRequest)

      println(result.getHeaders())
      result.getHeader("X-Gu-Tools-HMAC-Token").get().value() mustEqual "HMAC Y/ZHJ0K8Xe6OKsObyljzp6U03lSNm/hS1HDA2+KjYME="
      result.getHeader("X-Gu-Tools-HMAC-Date").get().value() mustEqual "Fri, 5 Apr 2019 01:02:03 GMT"
    }
  }

  "ArchiveHunterRequestor.decodeParsedData" should {
    "return a Right with collection and archivehunter ID when present" in {
      val testDataString = """{"status": "ok", "entryCount": 1, "entityClass": "archiveentry", "entries": [{"mimeType": {"major": "application", "minor": "octet-stream"}, "lightboxEntries": [], "file_extension": "mov", "proxied": false, "last_modified": "2015-08-27T14:39:24Z", "mediaMetadata": null, "path": "ARTE SHOWREEL/ARTE SHOWREEL.mov", "id": "wXstuPciiariu9l7Xow6wZ5ZwIvBu2ufBTAtIr9sJBC6knaaQbZweWMR0tMia0MuXZT+NV1AYHgNaVlrfY", "size": 693940155, "region": "eu-west-1", "bucket": "archive-collection-name", "etag": "0c02976c7326aacd17bb98dbdc467629-133", "storageClass": "GLACIER", "beenDeleted": false}]}""".stripMargin
      val maybeTestData = io.circe.parser.parse(testDataString)
      maybeTestData must beRight
      val testData = maybeTestData.right.get

      val rq = new ArchiveHunterRequestor("https://archivehunter.company.org","secretkey")(mock[ActorSystem],mock[Materializer])
      val result = rq.decodeParsedData(testData)
      result must beRight(ArchiveHunterFound("wXstuPciiariu9l7Xow6wZ5ZwIvBu2ufBTAtIr9sJBC6knaaQbZweWMR0tMia0MuXZT+NV1AYHgNaVlrfY","archive-collection-name",false))
    }

    "return a Left if the data is not present in the incoming json" in {
      val testDataString = """{}""".stripMargin
      val maybeTestData = io.circe.parser.parse(testDataString)
      maybeTestData must beRight
      val testData = maybeTestData.right.get

      val rq = new ArchiveHunterRequestor("https://archivehunter.company.org","secretkey")(mock[ActorSystem],mock[Materializer])
      val result = rq.decodeParsedData(testData)
      result must beLeft
    }

    "return a Left if one item is missing is not present in the incoming json" in {
      val testDataString = """{"status": "ok", "entryCount": 1, "entityClass": "archiveentry", "entries": [{"mimeType": {"major": "application", "minor": "octet-stream"}, "lightboxEntries": [], "file_extension": "mov", "proxied": false, "last_modified": "2015-08-27T14:39:24Z", "mediaMetadata": null, "path": "ARTE SHOWREEL/ARTE SHOWREEL.mov", "size": 693940155, "region": "eu-west-1", "bucket": "archive-collection-name", "etag": "0c02976c7326aacd17bb98dbdc467629-133", "storageClass": "GLACIER", "beenDeleted": false}]}""".stripMargin
      val maybeTestData = io.circe.parser.parse(testDataString)
      maybeTestData must beRight
      val testData = maybeTestData.right.get

      val rq = new ArchiveHunterRequestor("https://archivehunter.company.org","secretkey")(mock[ActorSystem],mock[Materializer])
      val result = rq.decodeParsedData(testData)
      result must beLeft
    }
  }
}
