import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import akka.stream.scaladsl.{GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import models.{UnclogStream, VidispineProject}
import org.specs2.mutable.Specification
import vidispine.VSFile
import scala.concurrent.Await
import scala.concurrent.duration._
import streamcomponents.SensitiveSwitch

class SensitiveSwitchSpec extends Specification {
  "SensitiveSwitch" should {
    "Push a report indicating true to YES" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[String],String](Seq())((acc,entry)=>acc++Seq(entry))

      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME

      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("New"), Option("VX-12"), true, false, true, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val switch = builder.add(new SensitiveSwitch)
        val merge = builder.add(Merge[String](2))
        src ~> switch
        switch.out(0).map(rpt=>"YES") ~> merge //YES branch
        switch.out(1).map(rpt=>"NO") ~> merge
        merge ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result mustEqual Seq("YES")
    }

    "Push a report indicating false to NO" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[String],String](Seq())((acc,entry)=>acc++Seq(entry))

      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME

      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("New"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val switch = builder.add(new SensitiveSwitch)
        val merge = builder.add(Merge[String](2))
        src ~> switch
        switch.out(0).map(rpt=>"YES") ~> merge //YES branch
        switch.out(1).map(rpt=>"NO") ~> merge
        merge ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result mustEqual Seq("NO")
    }

  }


}
