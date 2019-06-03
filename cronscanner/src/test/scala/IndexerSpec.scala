import java.time.ZonedDateTime
import java.util.UUID

import com.sksamuel.elastic4s.http.search.{SearchHits, SearchResponse}
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticError, RequestFailure, RequestSuccess, Shards}
import com.sksamuel.elastic4s.searches.SearchRequest
import models.JobHistory
import org.elasticsearch.client.Response
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class IndexerSpec extends Specification with Mockito{
  "Indexer.getReplicaStats" should {
    "convert the returned maps into a map of bucket size -> count" in {
      val mockedClient = mock[ElasticClient]
      val fakeAggs = Map("replicaCount"->Map("buckets"->List(
        Map("key"->1.0, "doc_count"->123),
        Map("key"->2.0, "doc_count"->234),
        Map("key"->3.0, "doc_count"->345)
      )))

      mockedClient.execute[SearchRequest,SearchResponse,Future](any)(any,any,any,any) returns
        Future(RequestSuccess[SearchResponse](200,
          None,
          Map(),
          SearchResponse(1234L,false,false,Map(),mock[Shards],None,fakeAggs,mock[SearchHits])
        ))

      val toTest = new Indexer("test")

      val result = Await.result(toTest.getReplicaStats(mockedClient), 30 seconds)

      result must beRight(Map(1.0->123, 2.0->234, 3.0->345))
    }

    "return an error as a string in a Left" in {
      val mockedClient = mock[ElasticClient]

      mockedClient.execute[SearchRequest,SearchResponse,Future](any)(any,any,any,any) returns
        Future(RequestFailure(400,
          None,
          Map(),
          ElasticError("sometype","some reason",None,None,None,Seq(),None)
        ))

      val toTest = new Indexer("test")

      val result = Await.result(toTest.getReplicaStats(mockedClient), 30 seconds)

      result must beLeft("ElasticError(sometype,some reason,None,None,None,List(),None)")
    }
  }

  "Indexer.calculateStats" should {
    "retrieve replica stats and update the provided JobHistoryModel" in {
      val mockedClient = mock[ElasticClient]

      val toTest = new Indexer("test") {
        override def getReplicaStats(esClient: ElasticClient): Future[Either[String, Map[Double, Int]]] = Future(Right(Map(1.0->123, 2.0->234, 3.0->345)))
      }

      val uuid = UUID.fromString("482FF934-2D16-4E3A-BA2D-8F6134BD87C2")
      val fakeStartTime = ZonedDateTime.now()
      val prevJobHistory = JobHistory(uuid,fakeStartTime,None,None,0,0,0)

      val result = Await.result(toTest.calculateStats(mockedClient, prevJobHistory), 30 seconds)
      result must beRight(JobHistory(uuid,fakeStartTime,None,None,123,234,345))
    }

    "pass along an error as a Left"  in {
      val mockedClient = mock[ElasticClient]

      val toTest = new Indexer("test") {
        override def getReplicaStats(esClient: ElasticClient): Future[Either[String, Map[Double, Int]]] = Future(Left("kaboom"))
      }

      val uuid = UUID.fromString("482FF934-2D16-4E3A-BA2D-8F6134BD87C2")
      val fakeStartTime = ZonedDateTime.now()
      val prevJobHistory = JobHistory(uuid,fakeStartTime,None,None,0,0,0)

      val result = Await.result(toTest.calculateStats(mockedClient, prevJobHistory), 30 seconds)
      result must beLeft(Seq("kaboom"))
    }
  }
}
