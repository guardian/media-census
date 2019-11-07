import java.time.ZonedDateTime

import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import streamComponents.VSStorageScanSource
import testhelpers.AkkaTestkitSpecs2Support
import vidispine.{VSCommunicator, VSFile, VSFileItemMembership, VSFileShapeMembership, VSFileState}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.Source

class VSStorageScanSourceSpec extends Specification with Mockito {
  def getTestData(filename:String) = {
    val s=Source.fromResource(filename)
    val content = s.mkString
    s.close()
    content
  }

  "VSStorageScanSource" should {
    "build and yield VSFile objects from incoming XML" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      val mockedCommunicator = mock[VSCommunicator]
      val sampleXml = getTestData("filescan.xml")
      mockedCommunicator.request(any, any,any,any,any,any)(any,any) returns Future(Right(sampleXml)) thenReturns Future(Right(getTestData("emptyscan.xml")))

      val sinkFac = Sink.fold[Seq[VSFile],VSFile](Seq())((acc,item)=>acc ++ Seq(item))

      val graph = GraphDSL.create(sinkFac) { implicit builder => sink =>
        import akka.stream.scaladsl.GraphDSL.Implicits._
        val src = builder.add(new VSStorageScanSource(Some("VX-1"), None, mockedCommunicator))

        src ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 10 seconds)
      result.head.path mustEqual "Multimedia_Culture_Sport/Ingest_from_Prelude_footage/richard_sprenger_Ingest_from_Prelude_footage/Untitled/BPAV/CLPR/001_1097_01/001_1097_01R01.BIM"
      result.head.state must beSome(VSFileState.CLOSED)
      result.head.size mustEqual 15809536
      result.head.hash must beSome("798e2b6284b3b31371e75e5c802ce21c545f07e8")
      result.head.timestamp mustEqual ZonedDateTime.parse("2017-07-27T03:51:17.210+01:00")

      result.head.membership must beSome(VSFileItemMembership("KP-1433",List(VSFileShapeMembership("KP-3812",List("KP-12716")))))
      result.length mustEqual 10
    }
  }
}
