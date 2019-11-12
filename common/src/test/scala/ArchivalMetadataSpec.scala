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
        Some(""),
        Some(ArchivalMetadata.AS_FAILED),
        Some("path/to/content"),
        Some("true"),
        None
      )
      mockedItem.getMoreMetadata(any)(any,any) returns Future(Right(mockedUpdatedItem))

      val result = Await.result(ArchivalMetadata.fromLazyItem(mockedItem,alwaysFetch=true), 1 second)

      result must beRight(ArchivalMetadata(
        ZonedDateTime.of(2019,1,2,3,4,5,0,ZoneId.of("UTC")),
        "somedevice",
        ArchivalMetadata.AR_NONE,
        "",
        ArchivalMetadata.AS_FAILED,
        "path/to/content",
        true,
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
        Some(""),
        Some(ArchivalMetadata.AS_FAILED),
        Some("path/to/content"),
        Some("true"),
        None
      )

      val result = Await.result(ArchivalMetadata.fromLazyItem(mockedItem,alwaysFetch=false), 1 second)

      result must beRight(ArchivalMetadata(
        ZonedDateTime.of(2019,1,2,3,4,5,0,ZoneId.of("UTC")),
        "somedevice",
        ArchivalMetadata.AR_NONE,
        "",
        ArchivalMetadata.AS_FAILED,
        "path/to/content",
        true,
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
}
