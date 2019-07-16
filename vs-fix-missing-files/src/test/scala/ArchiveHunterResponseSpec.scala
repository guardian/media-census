import java.time.ZonedDateTime

import helpers.ZonedDateTimeEncoder
import org.specs2.mutable.Specification
import io.circe.generic.auto._
import io.circe.syntax._
import mfmodels.{ArchiveHunterEntry, ArchiveHunterResponse, MimeType}

class ArchiveHunterResponseSpec extends Specification with ZonedDateTimeEncoder {
  "ArchiveHunterResponse" should {
    "automatically unmarshal sample data from AH" in {
      val sampleData = """{"status": "ok", "entryCount": 1, "entityClass": "archiveentry", "entries": [{"mimeType": {"major": "video", "minor": "mp4"}, "lightboxEntries": [], "file_extension": "mp4", "proxied": false, "last_modified": "2019-01-09T15:43:08Z", "mediaMetadata": null, "path": "vacuum.mp4", "id": "YXJjaGl2ZWh1bnRlci10ZXN0LW1lZGlhOnZhY3V1bS5tcDQ=", "size": 12819947, "region": "eu-west-1", "bucket": "archivehunter-test-media", "etag": "54dcb489cb40e89ef662009eaa9ad493-2", "storageClass": "STANDARD", "beenDeleted": false}]}"""

      val parsedData = io.circe.parser.parse(sampleData)

      parsedData must beRight

      val marshalledData = parsedData.right.get.as[ArchiveHunterResponse]

      marshalledData must beRight(ArchiveHunterResponse("archiveentry",1,"ok",Seq(
        ArchiveHunterEntry(false,"archivehunter-test-media","54dcb489cb40e89ef662009eaa9ad493-2","mp4",
          "YXJjaGl2ZWh1bnRlci10ZXN0LW1lZGlhOnZhY3V1bS5tcDQ=",ZonedDateTime.parse("2019-01-09T15:43:08Z"),Some(MimeType("video","mp4")),"vacuum.mp4",false,"eu-west-1",12819947L,"STANDARD")
      )))
    }
  }
}
