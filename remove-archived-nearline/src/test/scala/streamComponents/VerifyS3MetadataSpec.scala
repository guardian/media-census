package streamComponents
import java.time.ZonedDateTime

import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import com.amazonaws.services.s3.model.ObjectMetadata
import models.ArchivedItemRecord
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import testhelpers.AkkaTestkitSpecs2Support
import vidispine.VSFile

import scala.concurrent.Await
import scala.concurrent.duration._

class VerifyS3MetadataSpec extends Specification with Mockito{
  "VerifyS3Metadata" should {
    "push to YES if the fields in the VSFile match with the fields in the ObjectMetadata" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val fakeObjectMetadata = mock[ObjectMetadata]
      fakeObjectMetadata.getContentLength returns 1234L
      fakeObjectMetadata.getContentMD5 returns "fake-hash"

      val sampleRecord = ArchivedItemRecord(
        VSFile(
          "VX-1234","/some/path","file://some/path",None,1234L,Some("fake-hash"),ZonedDateTime.now(),1,"VX-21",None,None,None,None
        ),
        "some-bucket",
        "some/path",
        Some(fakeObjectMetadata)
      )

      val sinkFactory = Sink.seq[ArchivedItemRecord]
      val graph = GraphDSL.create(sinkFactory) {implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(sampleRecord))
        val toTest = builder.add(new VerifyS3Metadata)
        val ignore = builder.add(Sink.ignore)

        src ~> toTest
        toTest.out(0) ~> sink
        toTest.out(1) ~> ignore
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      result.head.s3Bucket mustEqual sampleRecord.s3Bucket
      result.head.s3Path mustEqual sampleRecord.s3Path
      result.head.s3Meta mustEqual sampleRecord.s3Meta
      result.head.nearlineItem mustEqual sampleRecord.nearlineItem.copy(archiveConflict = Some(false))
      result.length mustEqual 1
    }

    "push to YES if the size matches and there is no hash with the fields in the ObjectMetadata" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val fakeObjectMetadata = mock[ObjectMetadata]
      fakeObjectMetadata.getContentLength returns 1234L
      fakeObjectMetadata.getContentMD5 returns null

      val sampleRecord = ArchivedItemRecord(
        VSFile(
          "VX-1234","/some/path","file://some/path",None,1234L,Some("fake-hash"),ZonedDateTime.now(),1,"VX-21",None,None,None,None
        ),
        "some-bucket",
        "some/path",
        Some(fakeObjectMetadata)
      )

      val sinkFactory = Sink.seq[ArchivedItemRecord]
      val graph = GraphDSL.create(sinkFactory) {implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(sampleRecord))
        val toTest = builder.add(new VerifyS3Metadata)
        val ignore = builder.add(Sink.ignore)

        src ~> toTest
        toTest.out(0) ~> sink
        toTest.out(1) ~> ignore
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      result.head.s3Bucket mustEqual sampleRecord.s3Bucket
      result.head.s3Path mustEqual sampleRecord.s3Path
      result.head.s3Meta mustEqual sampleRecord.s3Meta
      result.head.nearlineItem mustEqual sampleRecord.nearlineItem.copy(archiveConflict = Some(false))
      result.length mustEqual 1
    }

    "push to YES if the size matches and there is no hash with the fields in the ObjectMetadata" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val fakeObjectMetadata = mock[ObjectMetadata]
      fakeObjectMetadata.getContentLength returns 1234L
      fakeObjectMetadata.getContentMD5 returns "some-hash"

      val sampleRecord = ArchivedItemRecord(
        VSFile(
          "VX-1234","/some/path","file://some/path",None,1234L,None,ZonedDateTime.now(),1,"VX-21",None,None,None,None
        ),
        "some-bucket",
        "some/path",
        Some(fakeObjectMetadata)
      )

      val sinkFactory = Sink.seq[ArchivedItemRecord]
      val graph = GraphDSL.create(sinkFactory) {implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(sampleRecord))
        val toTest = builder.add(new VerifyS3Metadata)
        val ignore = builder.add(Sink.ignore)

        src ~> toTest
        toTest.out(0) ~> sink
        toTest.out(1) ~> ignore
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      result.head.s3Bucket mustEqual sampleRecord.s3Bucket
      result.head.s3Path mustEqual sampleRecord.s3Path
      result.head.s3Meta mustEqual sampleRecord.s3Meta
      result.head.nearlineItem mustEqual sampleRecord.nearlineItem.copy(archiveConflict = Some(false))
      result.length mustEqual 1
    }

    "push to NO if the has does not match the ObjectMetadata" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val fakeObjectMetadata = mock[ObjectMetadata]
      fakeObjectMetadata.getContentLength returns 1234L
      fakeObjectMetadata.getContentMD5 returns "fake-hash-different"

      val sampleRecord = ArchivedItemRecord(
        VSFile(
          "VX-1234","/some/path","file://some/path",None,1234L,Some("fake-hash"),ZonedDateTime.now(),1,"VX-21",None,None,None,None
        ),
        "some-bucket",
        "some/path",
        Some(fakeObjectMetadata)
      )

      val sinkFactory = Sink.seq[ArchivedItemRecord]
      val graph = GraphDSL.create(sinkFactory) {implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(sampleRecord))
        val toTest = builder.add(new VerifyS3Metadata)
        val ignore = builder.add(Sink.ignore)

        src ~> toTest
        toTest.out(0) ~> ignore
        toTest.out(1) ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      result.head.s3Bucket mustEqual sampleRecord.s3Bucket
      result.head.s3Path mustEqual sampleRecord.s3Path
      result.head.s3Meta mustEqual sampleRecord.s3Meta
      result.head.nearlineItem mustEqual sampleRecord.nearlineItem.copy(archiveConflict = Some(true))
      result.length mustEqual 1
    }

    "push to NO if the size does not match the ObjectMetadata" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val fakeObjectMetadata = mock[ObjectMetadata]
      fakeObjectMetadata.getContentLength returns 12345L
      fakeObjectMetadata.getContentMD5 returns "fake-hash"

      val sampleRecord = ArchivedItemRecord(
        VSFile(
          "VX-1234","/some/path","file://some/path",None,1234L,Some("fake-hash"),ZonedDateTime.now(),1,"VX-21",None,None,None,None
        ),
        "some-bucket",
        "some/path",
        Some(fakeObjectMetadata)
      )

      val sinkFactory = Sink.seq[ArchivedItemRecord]
      val graph = GraphDSL.create(sinkFactory) {implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(sampleRecord))
        val toTest = builder.add(new VerifyS3Metadata)
        val ignore = builder.add(Sink.ignore)

        src ~> toTest
        toTest.out(0) ~> ignore
        toTest.out(1) ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      result.head.s3Bucket mustEqual sampleRecord.s3Bucket
      result.head.s3Path mustEqual sampleRecord.s3Path
      result.head.s3Meta mustEqual sampleRecord.s3Meta
      result.head.nearlineItem mustEqual sampleRecord.nearlineItem.copy(archiveConflict = Some(true))
      result.length mustEqual 1
    }
  }
}
