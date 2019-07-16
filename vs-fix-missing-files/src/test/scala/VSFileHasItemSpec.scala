import java.time.ZonedDateTime

import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, Merge, RunnableGraph, Sink, Source}
import mfstreamcomponents.VSFileHasItem
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import vidispine.{VSFile, VSFileItemMembership, VSFileState}

import scala.concurrent.Await
import scala.concurrent.duration._

class VSFileHasItemSpec extends Specification with Mockito {
  "VSFileHasItem" should {
    "push to YES if the pushed VSFile instance has item membership" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val testFile = VSFile("VX-1234","/path/to/file","proto://some-uri",Some(VSFileState.CLOSED),1234L,
        None,ZonedDateTime.now(),0,"VX-2",None,Some(VSFileItemMembership("VX-345",Seq())))

      val sinkFactory = Sink.fold[Seq[String],String](Seq())((acc, entry)=>acc++Seq(entry))


      val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.fromIterator(()=>Seq(testFile).toIterator))
        val gate = builder.add(new VSFileHasItem)
        val merge = builder.add(Merge[String](2))

        src ~> gate
        gate.out(0).map(_=>"yes") ~> merge //this way of testing is a bit nasty but it does the job
        gate.out(1).map(_=>"no") ~> merge
        merge ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)
      result mustEqual Seq("yes")
    }

    "push to NO if the pushed VSFile instance does not have item membership" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val testFile = VSFile("VX-1234","/path/to/file","proto://some-uri",Some(VSFileState.CLOSED),1234L,
        None,ZonedDateTime.now(),0,"VX-2",None,None)

      val sinkFactory = Sink.fold[Seq[String],String](Seq())((acc, entry)=>acc++Seq(entry))


      val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.fromIterator(()=>Seq(testFile).toIterator))
        val gate = builder.add(new VSFileHasItem)
        val merge = builder.add(Merge[String](2))
        src ~> gate
        gate.out(0).map(_=>"yes") ~> merge //this way of testing is a bit nasty but it does the job
        gate.out(1).map(_=>"no") ~> merge
        merge ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)
      result mustEqual Seq("no")
    }

  }
}
