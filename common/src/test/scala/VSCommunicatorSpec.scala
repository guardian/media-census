import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import vidispine.VSCommunicator
import com.softwaremill.sttp._
import testhelpers.AkkaTestkitSpecs2Support
import vidispine.VSCommunicator.OperationType

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class VSCommunicatorSpec extends Specification with Mockito{
  "VSCommunicator.request" should {
    "retry requests if it receives a 503" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val mockedSendGeneric = mock[(OperationType.Value, String,Option[String],Map[String,String],Map[String,String])=>Future[Response[Source[ByteString, Any]]]]
      mockedSendGeneric.apply(any,any,any,any,any) returns
        Future(Response[Source[ByteString, Any]](Left("Timeout"),503,scala.collection.immutable.Seq[(String,String)](),List())) thenReturns
        Future(Response[Source[ByteString, Any]](Right(Source.single(ByteString("eventual content"))),200,scala.collection.immutable.Seq[(String,String)](),List()))

      val comm = new VSCommunicator(uri"http://test-server.org","user","password") {
        override protected def sendGeneric(operation: VSCommunicator.OperationType.Value, uriPath: String, maybeXmlString: Option[String], headers: Map[String, String], queryParams: Map[String, String]): Future[Response[Source[ByteString, Any]]] =
          mockedSendGeneric(operation, uriPath, maybeXmlString, headers, queryParams)
      }

      val result = Await.result(comm.request(OperationType.GET,"/API/somepath",None,Map(),Map()), 30 seconds)
      result must beRight("eventual content")
      there were two(mockedSendGeneric).apply(any,any,any,any,any)
    }

    "retry requests if it receives a 500" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val mockedSendGeneric = mock[(OperationType.Value, String,Option[String],Map[String,String],Map[String,String])=>Future[Response[Source[ByteString, Any]]]]
      mockedSendGeneric.apply(any,any,any,any,any) returns
        Future(Response[Source[ByteString, Any]](Left("Timeout"),500,scala.collection.immutable.Seq[(String,String)](),List())) thenReturns
        Future(Response[Source[ByteString, Any]](Right(Source.single(ByteString("eventual content"))),200,scala.collection.immutable.Seq[(String,String)](),List()))

      val comm = new VSCommunicator(uri"http://test-server.org","user","password") {
        override protected def sendGeneric(operation: VSCommunicator.OperationType.Value, uriPath: String, maybeXmlString: Option[String], headers: Map[String, String], queryParams: Map[String, String]): Future[Response[Source[ByteString, Any]]] =
          mockedSendGeneric(operation, uriPath, maybeXmlString, headers, queryParams)
      }

      val result = Await.result(comm.request(OperationType.GET,"/API/somepath",None,Map(),Map()), 30 seconds)
      result must beRight("eventual content")
      there were two(mockedSendGeneric).apply(any,any,any,any,any)
    }

    "retry requests if it receives a 502" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val mockedSendGeneric = mock[(OperationType.Value, String,Option[String],Map[String,String],Map[String,String])=>Future[Response[Source[ByteString, Any]]]]
      mockedSendGeneric.apply(any,any,any,any,any) returns
        Future(Response[Source[ByteString, Any]](Left("Timeout"),502,scala.collection.immutable.Seq[(String,String)](),List())) thenReturns
        Future(Response[Source[ByteString, Any]](Right(Source.single(ByteString("eventual content"))),200,scala.collection.immutable.Seq[(String,String)](),List()))

      val comm = new VSCommunicator(uri"http://test-server.org","user","password") {
        override protected def sendGeneric(operation: VSCommunicator.OperationType.Value, uriPath: String, maybeXmlString: Option[String], headers: Map[String, String], queryParams: Map[String, String]): Future[Response[Source[ByteString, Any]]] =
          mockedSendGeneric(operation, uriPath, maybeXmlString, headers, queryParams)
      }

      val result = Await.result(comm.request(OperationType.GET,"/API/somepath",None,Map(),Map()), 30 seconds)
      result must beRight("eventual content")
      there were two(mockedSendGeneric).apply(any,any,any,any,any)
    }

    "not retry requests if it receives a 300" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val mockedSendGeneric = mock[(OperationType.Value, String,Option[String],Map[String,String],Map[String,String])=>Future[Response[Source[ByteString, Any]]]]
      mockedSendGeneric.apply(any,any,any,any,any) returns
        Future(Response[Source[ByteString, Any]](Left("Bad data"),400,scala.collection.immutable.Seq[(String,String)](),List())) thenReturns
        Future(Response[Source[ByteString, Any]](Right(Source.single(ByteString("eventual content"))),200,scala.collection.immutable.Seq[(String,String)](),List()))

      val comm = new VSCommunicator(uri"http://test-server.org","user","password") {
        override protected def sendGeneric(operation: VSCommunicator.OperationType.Value, uriPath: String, maybeXmlString: Option[String], headers: Map[String, String], queryParams: Map[String, String]): Future[Response[Source[ByteString, Any]]] =
          mockedSendGeneric(operation, uriPath, maybeXmlString, headers, queryParams)
      }

      val result = Await.result(comm.request(OperationType.GET,"/API/somepath",None,Map(),Map()), 30 seconds)
      result must beLeft
      there was one(mockedSendGeneric).apply(any,any,any,any,any)
    }

    "retry requests if it receives a 200" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val mockedSendGeneric = mock[(OperationType.Value, String,Option[String],Map[String,String],Map[String,String])=>Future[Response[Source[ByteString, Any]]]]
      mockedSendGeneric.apply(any,any,any,any,any) returns
        Future(Response[Source[ByteString, Any]](Right(Source.single(ByteString("my content"))),200,scala.collection.immutable.Seq[(String,String)](),List()))

      val comm = new VSCommunicator(uri"http://test-server.org","user","password") {
        override protected def sendGeneric(operation: VSCommunicator.OperationType.Value, uriPath: String, maybeXmlString: Option[String], headers: Map[String, String], queryParams: Map[String, String]): Future[Response[Source[ByteString, Any]]] =
          mockedSendGeneric(operation, uriPath, maybeXmlString, headers, queryParams)
      }

      val result = Await.result(comm.request(OperationType.GET,"/API/somepath",None,Map(),Map()), 30 seconds)
      result must beRight("my content")
      there was one(mockedSendGeneric).apply(any,any,any,any,any)
    }

  }

  "VSCommunicator.consumeSource" should {
    "convert all of the incoming stream into a single ByteString, convert that to a regular UTF string and return it in a future" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val comm = new VSCommunicator(uri"http://test-server.org", "user","password") {
        def testConsume(source:Source[ByteString,Any]) = consumeSource(source)
      }

      val src = Source.fromIterator(()=>Seq(ByteString("123"),ByteString("456"),ByteString("789")).toIterator)

      val result = Await.result(comm.testConsume(src), 30 seconds)
      result mustEqual "123456789"
    }
  }


}
