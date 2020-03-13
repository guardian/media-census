import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import models.{MediaStatusValue, UnclogStream, VidispineProject}
import org.specs2.mutable.Specification
import streamcomponents.SetFlagsShape
import vidispine.VSFile

import scala.concurrent.Await
import scala.concurrent.duration._

class SetFlagsShapeSpec extends Specification {
  "SetFlagsShape" should {
    "Push an object including the SENSITIVE flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("New"), Option("VX-12"), true, false, true, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("New"), Option("VX-12"), true, false, true, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), Some(MediaStatusValue.SENSITIVE))
    }
    "Push an object including the PROJECT_OPEN flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("New"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("New"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), Some(MediaStatusValue.PROJECT_OPEN))
    }
    "Push an object including the PROJECT_OPEN flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("In Production"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("In Production"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), Some(MediaStatusValue.PROJECT_OPEN))
    }
    "Push an object including the PROJECT_OPEN flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Restore"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Restore"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), Some(MediaStatusValue.PROJECT_OPEN))
    }
    "Push an object including the PROJECT_HELD flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Held"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Held"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), Some(MediaStatusValue.PROJECT_HELD))
    }
    "Push an object including the NO_PROJECT flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(), Some(MediaStatusValue.NO_PROJECT))
    }
    "Push an object including the DELETABLE flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Killed"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Killed"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), Some(MediaStatusValue.DELETABLE))
    }
    "Push an object including the DELETABLE flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Completed"), Option("VX-12"), true, true, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Completed"), Option("VX-12"), true, true, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), Some(MediaStatusValue.DELETABLE))
    }
    "Push an object including the SHOULD_BE_ARCHIVED_AND_DELETED flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Completed"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Completed"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), Some(MediaStatusValue.SHOULD_BE_ARCHIVED_AND_DELETED))
    }
    "Push an object including the UNKNOWN flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Wibble"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Wibble"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), Some(MediaStatusValue.UNKNOWN))
    }
    "Push an object including the SHOULD_BE_ARCHIVED_AND_DELETED flag to the out" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val sinkFactory = Sink.fold[Seq[(UnclogStream)], UnclogStream](Seq())((acc,entry)=>acc++Seq(entry))
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val testStream = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Completed"), Option("VX-12"), false, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern))), VidispineProject("VX-1234", Option("Test Name"), Option("Completed"), Option("VX-12"), false, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern))), VidispineProject("VX-1234", Option("Test Name"), Option("Completed"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), None)))
        val toTest = builder.add(new SetFlagsShape)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(testStream).run(), 30 seconds)
      result.head mustEqual UnclogStream(VSFile("VX-123344", "/test", "/test", None, 2318793, None, ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern), 1, "VX-4", None, None, None, None),None,Seq(VidispineProject("VX-1234", Option("Test Name"), Option("Completed"), Option("VX-12"), false, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern))), VidispineProject("VX-1234", Option("Test Name"), Option("Completed"), Option("VX-12"), false, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern))), VidispineProject("VX-1234", Option("Test Name"), Option("Completed"), Option("VX-12"), true, false, false, Option(ZonedDateTime.parse("2019-07-17T10:35:12.598Z", pattern)), Option(ZonedDateTime.parse("2019-10-16T04:00:00.523+01:00", pattern)))), Some(MediaStatusValue.SHOULD_BE_ARCHIVED_AND_DELETED))
    }
  }
}
