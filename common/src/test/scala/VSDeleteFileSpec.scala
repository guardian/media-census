import java.time.ZonedDateTime

import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import models.HttpError
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import testhelpers.AkkaTestkitSpecs2Support
import vidispine.VSCommunicator.OperationType
import vidispine.{GetMetadataError, VSCommunicator, VSFile}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

class VSDeleteFileSpec extends Specification with Mockito {
  "VSDeleteFile" should {
    "send a DELETE request to the relevant URI when it receives a file" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val mockedVSCommunicator = mock[VSCommunicator]
      mockedVSCommunicator.request(any,any,any,any,any,any)(any,any) returns Future(Right(""))

      val initialFile = VSFile("VX-1234","path/to/file","file://path/to/file",None,1234L,None,ZonedDateTime.now(),0,"VX-1",None,None,None,None)
      val sinkFact = Sink.seq[VSFile]
      val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._
        val src = Source.single(initialFile)
        val toTest = builder.add(new VSDeleteFile())

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 10 seconds)
      there was one(mockedVSCommunicator).request(OperationType.DELETE,"/API/file/VX-1234",None,Map(),Map())
    }

    "not fail if a 404 is returned" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val mockedVSCommunicator = mock[VSCommunicator]
      mockedVSCommunicator.request(any,any,any,any,any,any)(any,any) returns Future(Left(HttpError("Not found", 404)))

      val initialFile = VSFile("VX-1234","path/to/file","file://path/to/file",None,1234L,None,ZonedDateTime.now(),0,"VX-1",None,None,None,None)
      val sinkFact = Sink.seq[VSFile]
      val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._
        val src = Source.single(initialFile)
        val toTest = builder.add(new VSDeleteFile())

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 10 seconds)
      there was one(mockedVSCommunicator).request(OperationType.DELETE,"/API/file/VX-1234",None,Map(),Map())
    }

    "fail if the server returns an error" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val mockedVSCommunicator = mock[VSCommunicator]
      mockedVSCommunicator.request(any,any,any,any,any,any)(any,any) returns Future(Left(HttpError("Kaboom", 500)))

      val initialFile = VSFile("VX-1234","path/to/file","file://path/to/file",None,1234L,None,ZonedDateTime.now(),0,"VX-1",None,None,None,None)
      val sinkFact = Sink.seq[VSFile]
      val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._
        val src = Source.fromIterator(()=>Seq(initialFile,initialFile).toIterator)
        val toTest = builder.add(new VSDeleteFile())

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Try { Await.result(RunnableGraph.fromGraph(graph).run(), 10 seconds) }
      there was one(mockedVSCommunicator).request(OperationType.DELETE,"/API/file/VX-1234",None,Map(),Map())
      result must beFailedTry
    }

    "fail if the lookup future fails" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val mockedVSCommunicator = mock[VSCommunicator]
      mockedVSCommunicator.request(any,any,any,any,any,any)(any,any) returns Future.failed(new RuntimeException("kaboom"))

      val initialFile = VSFile("VX-1234","path/to/file","file://path/to/file",None,1234L,None,ZonedDateTime.now(),0,"VX-1",None,None,None,None)
      val sinkFact = Sink.seq[VSFile]
      val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._
        val src = Source.fromIterator(()=>Seq(initialFile,initialFile).toIterator)
        val toTest = builder.add(new VSDeleteFile())

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Try { Await.result(RunnableGraph.fromGraph(graph).run(), 10 seconds) }
      there was one(mockedVSCommunicator).request(OperationType.DELETE,"/API/file/VX-1234",None,Map(),Map())
      result must beFailedTry
    }
  }
}
