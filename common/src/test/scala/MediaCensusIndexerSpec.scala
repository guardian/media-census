import java.time.ZonedDateTime
import java.util.UUID

import com.sksamuel.elastic4s.http.search.{SearchHits, SearchResponse}
import com.sksamuel.elastic4s.http._
import com.sksamuel.elastic4s.searches.SearchRequest
import models.{JobHistory, MediaCensusIndexer, StatsEntry}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class MediaCensusIndexerSpec extends Specification with Mockito{
  "Indexer.getReplicaStats" should {
    "convert the returned maps into a map of bucket size -> count" in {
      val mockedClient = mock[ElasticClient]
      val fakeAggs = Map("replicaCount"->Map("buckets"->List(
        Map("key"->1.0, "doc_count"->123, "totalSize"->Map("value"->702.0)),
        Map("key"->2.0, "doc_count"->234, "totalSize"->Map("value"->702.0)),
        Map("key"->3.0, "doc_count"->345, "totalSize"->Map("value"->702.0))
      )))

      mockedClient.execute[SearchRequest,SearchResponse,Future](any)(any,any,any,any) returns
        Future(RequestSuccess[SearchResponse](200,
          None,
          Map(),
          SearchResponse(1234L,isTimedOut = false,isTerminatedEarly = false,Map(),mock[Shards],None,fakeAggs,mock[SearchHits])
        ))

      val toTest = new MediaCensusIndexer("test")

      val result = Await.result(toTest.getReplicaStats(mockedClient), 30 seconds)

      result must beRight(List(StatsEntry("1.0",123,702), StatsEntry("2.0",234,702), StatsEntry("3.0",345,702)))
    }

    "return an error as a string in a Left" in {
      val mockedClient = mock[ElasticClient]

      mockedClient.execute[SearchRequest,SearchResponse,Future](any)(any,any,any,any) returns
        Future(RequestFailure(400,
          None,
          Map(),
          ElasticError("sometype","some reason",None,None,None,Seq(),None)
        ))

      val toTest = new MediaCensusIndexer("test")

      val result = Await.result(toTest.getReplicaStats(mockedClient), 30 seconds)

      result must beLeft("ElasticError(sometype,some reason,None,None,None,List(),None)")
    }
  }

  "Indexer.calculateStats" should {
    "retrieve replica stats and update the provided JobHistoryModel" in {
      val mockedClient = mock[ElasticClient]

      val toTest = new MediaCensusIndexer("test") {
        override def getReplicaStats(esClient: ElasticClient): Future[Either[String, List[StatsEntry]]] = Future(Right(List(
          StatsEntry("1.0",123,702),
          StatsEntry("2.0",234,702),
          StatsEntry("3.0",345,702)
        )))
          //Future(Right(Map(1.0->123, 2.0->234, 3.0->345)))

        override def getUnattachedCount(esClient: ElasticClient): Future[Either[String, Long]] = Future(Right(456))

        override def getUnimportedCount(esClient: ElasticClient): Future[Either[String, Long]] = Future(Right(567))
      }

      val uuid = UUID.fromString("482FF934-2D16-4E3A-BA2D-8F6134BD87C2")
      val fakeStartTime = ZonedDateTime.now()
      val prevJobHistory = JobHistory(uuid,None, fakeStartTime,None,None,0,0,0,0,0,0)

      val result = Await.result(toTest.calculateStats(mockedClient, prevJobHistory), 30 seconds)
      result must beRight(JobHistory(uuid,None, fakeStartTime,None,None,123,234,345, 567, 456,0))
    }

    "pass along an error as a Left"  in {
      val mockedClient = mock[ElasticClient]

      val toTest = new MediaCensusIndexer("test") {
        override def getReplicaStats(esClient: ElasticClient): Future[Either[String, List[StatsEntry]]] = Future(Left("kaboom"))

        override def getUnattachedCount(esClient: ElasticClient): Future[Either[String, Long]] = Future(Right(456))

        override def getUnimportedCount(esClient: ElasticClient): Future[Either[String, Long]] = Future(Right(567))
      }

      val uuid = UUID.fromString("482FF934-2D16-4E3A-BA2D-8F6134BD87C2")
      val fakeStartTime = ZonedDateTime.now()
      val prevJobHistory = JobHistory(uuid,None,fakeStartTime,None,None,0,0,0,0,0,0)

      val result = Await.result(toTest.calculateStats(mockedClient, prevJobHistory), 30 seconds)
      result must beLeft(Seq("kaboom"))
    }
  }

  "Indexer.calculateStatsRaw" should {
    "remove zero-sized bins if includeZeros is false" in {
      val mockedClient = mock[ElasticClient]

      val toTest = new MediaCensusIndexer("test") {
        override def getReplicaStats(esClient: ElasticClient): Future[Either[String, List[StatsEntry]]] = Future(Right(List(
          StatsEntry("0.0",222,724),
          StatsEntry("1.0",123,724),
          StatsEntry("2.0",234,724),
          StatsEntry("3.0",345,724)
        )))

        override def getUnattachedCount(esClient: ElasticClient): Future[Either[String, Long]] = Future(Right(1L))

        override def getUnimportedCount(esClient: ElasticClient): Future[Either[String, Long]] = Future(Right(2L))
      }

      val result = Await.result(toTest.calculateStatsRaw(mockedClient, includeZeroes = false), 2 seconds)
      result must beRight(List(StatsEntry("1.0",123,724), StatsEntry("2.0",234,724), StatsEntry("3.0",345,724)),1L,2L)
    }

    "include zero-sized bins if includeZeroes is true" in {
      val mockedClient = mock[ElasticClient]

      val toTest = new MediaCensusIndexer("test") {
        override def getReplicaStats(esClient: ElasticClient): Future[Either[String, List[StatsEntry]]] = Future(Right(List(
          StatsEntry("0.0",222,724),
          StatsEntry("1.0",123,724),
          StatsEntry("2.0",234,724),
          StatsEntry("3.0",345,724)
        )))

        override def getUnattachedCount(esClient: ElasticClient): Future[Either[String, Long]] = Future(Right(1L))

        override def getUnimportedCount(esClient: ElasticClient): Future[Either[String, Long]] = Future(Right(2L))
      }

      val result = Await.result(toTest.calculateStatsRaw(mockedClient, includeZeroes = true), 2 seconds)
      result must beRight(List(StatsEntry("0.0",222,724), StatsEntry("1.0",123,724), StatsEntry("2.0",234,724), StatsEntry("3.0",345,724)),1L,2L)
    }
  }
}
