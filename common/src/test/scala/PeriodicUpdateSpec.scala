import java.time.ZonedDateTime
import java.util.UUID

import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import com.sksamuel.elastic4s.http.ElasticClient
import models.{AssetSweeperFile, JobHistory, JobHistoryDAO, MediaCensusEntry, MediaCensusIndexer}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import streamComponents.PeriodicUpdate

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class PeriodicUpdateSpec extends Specification with Mockito{
  sequential

  "PeriodicUpdateSpec" should {
    "call out to JobModelDAO to update stats at intervals" in new AkkaTestkitSpecs2Support {
      implicit val ec:ExecutionContext = system.dispatcher
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val uuid = UUID.randomUUID()
      val mockedJobEntry = JobHistory(uuid, None, ZonedDateTime.now, None,None,0,0,0,0,0,0)
      val updatedJobEntry = mockedJobEntry.copy(fullBackupsCount = 10)

      implicit val mockedJobHistoryDAO = mock[JobHistoryDAO]
      mockedJobHistoryDAO.jobForUuid(any) returns Future(Right(Some(mockedJobEntry)))
      mockedJobHistoryDAO.put(any) returns Future(Right(2L))

      implicit val mockedIndexer = mock[MediaCensusIndexer]
      mockedIndexer.calculateStats(any, any) returns Future(Right(updatedJobEntry))

      implicit val esClient = mock[ElasticClient]

      val sinkFactory = Sink.ignore

      val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.fromIterator[MediaCensusEntry](()=>Array(
          MediaCensusEntry(mock[AssetSweeperFile],None,None,None,None,None,Seq(),3),
          MediaCensusEntry(mock[AssetSweeperFile],None,None,None,None,None,Seq(),3),
          MediaCensusEntry(mock[AssetSweeperFile],None,None,None,None,None,Seq(),3),
          MediaCensusEntry(mock[AssetSweeperFile],None,None,None,None,None,Seq(),3),
        ).toIterator))

        val toTest = builder.add(new PeriodicUpdate(mockedJobEntry,updateEvery=3))

        src ~> toTest ~> sink
        ClosedShape
      }

      //run the stream
      Await.ready(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      there was one(mockedJobHistoryDAO).jobForUuid(uuid)
      there was one(mockedJobHistoryDAO).put(mockedJobEntry.copy(fullBackupsCount=10, itemsCounted=4L))

    }
  }
}
