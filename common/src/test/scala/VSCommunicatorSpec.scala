import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import vidispine.VSCommunicator
import com.softwaremill.sttp._
import testhelpers.AkkaTestkitSpecs2Support

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class VSCommunicatorSpec extends Specification with Mockito{
  "VSCommunicator.requestGet" should {
    "retry requests if it receives a 503" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val mockedSendGet = mock[Function3[String,Map[String,String],Map[String,String], Future[Response[Source[ByteString,Any]]]]]
      mockedSendGet.apply(any,any,any) returns
        Future(Response[Source[ByteString, Any]](Left("Timeout"),503,scala.collection.immutable.Seq[(String,String)](),List())) thenReturns
      Future(Response[Source[ByteString, Any]](Right(Source.single(ByteString("eventual content"))),200,scala.collection.immutable.Seq[(String,String)](),List()))

      val comm = new VSCommunicator(uri"http://test-server.org","user","password") {
        override protected def sendGet(uriPath:String, headers:Map[String,String], queryParams:Map[String,String]) = mockedSendGet(uriPath, headers, queryParams)
      }


      val result = Await.result(comm.requestGet("/API/somepath",Map(),Map()), 30 seconds)
      result must beRight("eventual content")
      there were two(mockedSendGet).apply(any,any,any)
    }

    "retry requests if it receives a 500" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val mockedSendGet = mock[Function3[String,Map[String,String],Map[String,String], Future[Response[Source[ByteString,Any]]]]]
      mockedSendGet.apply(any,any,any) returns
        Future(Response[Source[ByteString, Any]](Left("Server error"),500,scala.collection.immutable.Seq[(String,String)](),List())) thenReturns
        Future(Response[Source[ByteString, Any]](Right(Source.single(ByteString("eventual content"))),200,scala.collection.immutable.Seq[(String,String)](),List()))

      val comm = new VSCommunicator(uri"http://test-server.org","user","password") {
        override protected def sendGet(uriPath:String, headers:Map[String,String], queryParams:Map[String,String]) = mockedSendGet(uriPath, headers, queryParams)
      }


      val result = Await.result(comm.requestGet("/API/somepath",Map(),Map()), 30 seconds)
      result must beRight("eventual content")
      there were two(mockedSendGet).apply(any,any,any)
    }

    "not retry requests if it receives a 400" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val mockedSendGet = mock[Function3[String,Map[String,String],Map[String,String], Future[Response[Source[ByteString,Any]]]]]
      mockedSendGet.apply(any,any,any) returns
        Future(Response[Source[ByteString, Any]](Left("Bad data"),400,scala.collection.immutable.Seq[(String,String)](),List())) thenReturns
        Future(Response[Source[ByteString, Any]](Right(Source.single(ByteString("eventual content"))),200,scala.collection.immutable.Seq[(String,String)](),List()))

      val comm = new VSCommunicator(uri"http://test-server.org","user","password") {
        override protected def sendGet(uriPath:String, headers:Map[String,String], queryParams:Map[String,String]) = mockedSendGet(uriPath, headers, queryParams)
      }


      val result = Await.result(comm.requestGet("/API/somepath",Map(),Map()), 30 seconds)
      result must beLeft
      there was one(mockedSendGet).apply(any,any,any)
    }
  }


}
