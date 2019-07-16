import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, Merge, RunnableGraph, Sink, Source}
import mfstreamcomponents.VSItemHasArchivePath
import org.specs2.mutable.Specification
import vidispine.{FieldNames, VSEntry, VSLazyItem, VSMetadataEntry}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class VSItemHasArchivePathSpec extends Specification{
  def buildGraph(sinkFactory:Sink[String,Future[Seq[String]]], testEntry:VSEntry) = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
    import akka.stream.scaladsl.GraphDSL.Implicits._

    val src = builder.add(Source.fromIterator(()=>Seq(testEntry).toIterator))
    val gate = builder.add(new VSItemHasArchivePath)
    val merge = builder.add(Merge[String](2))

    src ~> gate
    gate.out(0).map(_=>"yes") ~> merge
    gate.out(1).map(_=>"no") ~> merge
    merge ~> sink
    ClosedShape
  }

  "VSItemHasArchivePath" should {
    "extract metadata from the given item and test for external archive data, pushing to YES if the data is present" in  new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val testItem = VSLazyItem("VX-1234",Map(
        FieldNames.EXTERNAL_ARCHIVE_STATUS->VSMetadataEntry.simple(FieldNames.EXTERNAL_ARCHIVE_STATUS,"Archive Requested"),
        FieldNames.EXTERNAL_ARCHIVE_PATH->VSMetadataEntry.simple(FieldNames.EXTERNAL_ARCHIVE_PATH, "some/path/to/file"),
        FieldNames.EXTERNAL_ARCHIVE_DEVICE->VSMetadataEntry.simple(FieldNames.EXTERNAL_ARCHIVE_DEVICE, "archivedevice")
      ))

      val testEntry = VSEntry(
        None,
        Some(testItem),
        None
      )

      val sinkFactory = Sink.fold[Seq[String],String](Seq())((acc,entry)=>acc++Seq(entry))
      val graph = buildGraph(sinkFactory, testEntry)

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      result mustEqual Seq("yes")
    }

    "extract metadata from the given item and test for external archive data, pushing to NO if the data is not present" in  new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val testItem = VSLazyItem("VX-1234",Map())

      val testEntry = VSEntry(
        None,
        Some(testItem),
        None
      )

      val sinkFactory = Sink.fold[Seq[String],String](Seq())((acc,entry)=>acc++Seq(entry))
      val graph = buildGraph(sinkFactory, testEntry)

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      result mustEqual Seq("no")
    }

    "push to No if no item or no metadata is present" in  new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val testEntry = VSEntry(
        None,
        None,
        None
      )

      val sinkFactory = Sink.fold[Seq[String],String](Seq())((acc,entry)=>acc++Seq(entry))
      val graph = buildGraph(sinkFactory, testEntry)

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      result mustEqual Seq("no")
    }
  }
}
