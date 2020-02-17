import java.time.{ZoneId, ZonedDateTime}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import vidispine.{ArchivalMetadata, GetMetadataError, VSCommunicator, VSLazyItem}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ArchivalMetadataSpec extends Specification with Mockito {
  "ArchivalMetadata.fetchForItem" should {
    "call out to the item to populate metadata" in {
      implicit val fakeComm = mock[VSCommunicator]
      implicit val fakeMat = mock[Materializer]

      val mockedItem = mock[VSLazyItem]
      val mockedUpdatedItem = mock[VSLazyItem]

      mockedItem.getMoreMetadata(any)(any, any) returns Future(Right(mockedUpdatedItem))

      val result = Await.result(ArchivalMetadata.fetchForItem(mockedItem), 1 seconds)

      result must beRight(mockedUpdatedItem)
      there was one(mockedItem).getMoreMetadata(ArchivalMetadata.interestingFields)
    }
  }

  "ArchivalMetadata.fromLazyItem" should {
    "optionally populate the metadata for an item and translate it" in {
      implicit val fakeComm = mock[VSCommunicator]
      implicit val fakeMat = mock[Materializer]

      val mockedItem = mock[VSLazyItem]
      val mockedUpdatedItem = mock[VSLazyItem]

      mockedUpdatedItem.getSingle(any) returns (
        Some("2019-01-02T03:04:05Z[UTC]"),
        Some("somedevice"),
        Some(ArchivalMetadata.AR_NONE),
        None,
        Some(ArchivalMetadata.AS_FAILED),
        Some("path/to/content"),
        Some("true"),
        None
      )
      mockedItem.getMoreMetadata(any)(any,any) returns Future(Right(mockedUpdatedItem))

      val result = Await.result(ArchivalMetadata.fromLazyItem(mockedItem,alwaysFetch=true), 1 second)

      result must beRight(ArchivalMetadata(
        Some(ZonedDateTime.of(2019,1,2,3,4,5,0,ZoneId.of("UTC"))),
        Some("somedevice"),
        Some(ArchivalMetadata.AR_NONE),
        None,
        Some(ArchivalMetadata.AS_FAILED),
        Some("path/to/content"),
        Some(true),
        None
      ))

      there was one(mockedItem).getMoreMetadata(any)(any,any)
    }

    "optionally not populate the metadata for an item but still translate it" in {
      implicit val fakeComm = mock[VSCommunicator]
      implicit val fakeMat = mock[Materializer]

      val mockedItem = mock[VSLazyItem]
      val mockedUpdatedItem = mock[VSLazyItem]

      mockedItem.getSingle(any) returns (
        Some("2019-01-02T03:04:05Z[UTC]"),
        Some("somedevice"),
        Some(ArchivalMetadata.AR_NONE),
        None,
        Some(ArchivalMetadata.AS_FAILED),
        Some("path/to/content"),
        Some("true"),
        None
      )

      val result = Await.result(ArchivalMetadata.fromLazyItem(mockedItem,alwaysFetch=false), 1 second)

      result must beRight(ArchivalMetadata(
        Some(ZonedDateTime.of(2019,1,2,3,4,5,0,ZoneId.of("UTC"))),
        Some("somedevice"),
        Some(ArchivalMetadata.AR_NONE),
        None,
        Some(ArchivalMetadata.AS_FAILED),
        Some("path/to/content"),
        Some(true),
        None
      ))

      there was no(mockedItem).getMoreMetadata(any)(any,any)
    }

    "pass on a lookup error" in {
      implicit val fakeComm = mock[VSCommunicator]
      implicit val fakeMat = mock[Materializer]

      val mockedItem = mock[VSLazyItem]
      val mockedUpdatedItem = mock[VSLazyItem]

      mockedUpdatedItem.getSingle(any) returns (
        Some("2019-01-02T03:04:05Z[UTC]"),
        Some("somedevice"),
        Some(ArchivalMetadata.AR_NONE),
        Some(""),
        Some(ArchivalMetadata.AS_FAILED),
        Some("path/to/content"),
        Some("true"),
        None
      )
      mockedItem.getMoreMetadata(any)(any, any) returns Future(Left(GetMetadataError(None,None,None)))

      val result = Await.result(ArchivalMetadata.fromLazyItem(mockedItem,alwaysFetch=true), 1 second)

      result must beLeft(GetMetadataError(None,None,None))

      there was one(mockedItem).getMoreMetadata(any)(any,any)
    }
  }

  "ArchivalMetadata.makeXML" should {
    "render out XML with only the set fields in it" in {
      val toTest = ArchivalMetadata(
        Some(ZonedDateTime.of(2019,1,2,3,4,5,0,ZoneId.systemDefault())),
        Some("somedevice"),
        Some(ArchivalMetadata.AR_REQUESTED),
        None,
        Some(ArchivalMetadata.AS_RUNNING),
        Some("path/to/somefile"),
        Some(true),
        None
      )

      val result = toTest.makeXml
      println(s"Got $result")

      val fields = result \ "field"
      val committedNode = fields.head
      (committedNode \ "name").text mustEqual "gnm_external_archive_committed_to_archive_at"
      (committedNode \ "value").text mustEqual "2019-01-02T03:04:05Z"

      val deviceNode = fields(1)
      (deviceNode \ "name").text mustEqual "gnm_external_archive_external_archive_device"
      (deviceNode \ "value").text mustEqual "somedevice"

      val requestNode = fields(2)
      (requestNode \ "name").text mustEqual "gnm_external_archive_external_archive_request"
      (requestNode \ "value").text mustEqual "Requested Archive"
    }
  }
}
