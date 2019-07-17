import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import streamComponents.VSItemSearchSource
import testhelpers.AkkaTestkitSpecs2Support
import vidispine.{FieldNames, VSCommunicator, VSLazyItem}

import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class VSItemSearchSourceSpec extends Specification with Mockito{
  def getTestSearch(filename:String) = {
    val s=Source.fromResource(filename)
    val content = s.mkString
    s.close()
    content
  }

  "VSItemSearchSource" should {
    "load and parse an XML document from the server" in  new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      implicit val mockedComm = mock[VSCommunicator]
      mockedComm.request(any,any,any,any,any)(any,any) returns Future(Right(getTestSearch("testsearch.xml"))) thenReturns Future(Right(getTestSearch("emptysearch.xml")))

      val sinkFactory = Sink.fold[Seq[VSLazyItem],VSLazyItem](Seq())((acc,entry)=>acc++Seq(entry))

      val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(new VSItemSearchSource(Seq("field1","field2"),"mock-search-doc",true))
        src ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)
      println(s"Got ${result.length} items:")
      result.foreach(item=>println(s"\t$item"))
      result.length mustEqual 18

      there were two(mockedComm).request("/API/item;first=1;number=100","mock-search-doc",Map("Accept"->"application/xml"),Map("content"->"metadata","field"->"field1,field2"))

      result.head.getSingle(FieldNames.EXTERNAL_ARCHIVE_STATUS) must beSome("Archived")
      //there was one(mockedComm).request("/API/item;first=101;number=100","mock-search-doc",Map("Accept"->"application/xml"),Map("content"->"metadata","field"->"field1,field2"))
    }
  }
}
