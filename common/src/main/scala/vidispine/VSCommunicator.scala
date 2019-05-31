package vidispine

import java.util.Base64

import akka.actor.ActorSystem
import akka.event.DiagnosticLoggingAdapter
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import models.HttpError
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class VSCommunicator(vsUri:Uri, plutoUser:String, plutoPass:String)(implicit val actorSystem:ActorSystem) {

  private val logger = LoggerFactory.getLogger(getClass)

  private def authString:String = Base64.getEncoder.encodeToString(s"$plutoUser:$plutoPass".getBytes)

  protected implicit val sttpBackend:SttpBackend[Future,Source[ByteString, Any]] = AkkaHttpBackend.usingActorSystem(actorSystem)

  private def sendPut(uriPath:String, xmlString:String, headers:Map[String,String]):Future[Response[Source[ByteString, Any]]] = {
    val bs = ByteString(xmlString,"UTF-8")

    val uri = vsUri.path(uriPath)
    logger.info("Got xml body")
    val source:Source[ByteString, Any] = Source.single(bs)
    val hdr = Map(
      "Accept"->"application/xml",
      "Authorization"->s"Basic $authString",
      "Content-Type"->"application/xml"
    ) ++ headers

    logger.debug(s"Got headers, initiating send to $uri")
    sttp
      .put(uri)
      .streamBody(source)
      .headers(hdr)
      .response(asStream[Source[ByteString, Any]])
      .send()
  }

  private def sendGet(uriPath:String, headers:Map[String,String], queryParams:Map[String,String]):Future[Response[Source[ByteString, Any]]] = {
    val hdr = Map(
      "Accept"->"application/xml",
      "Authorization"->s"Basic $authString",
      "Content-Type"->"application/xml"
    ) ++ headers

    val uriWithPath = vsUri.path(uriPath)
    val uri = queryParams.foldLeft[Uri](uriWithPath)((acc, tuple)=>acc.queryFragment(Uri.QueryFragment.KeyValue(tuple._1,tuple._2)))

    logger.debug(s"Got headers, initiating send to $uri")
    sttp
      .get(uri)
      .headers(hdr)
      .response(asStream[Source[ByteString, Any]])
      .send()
  }

  private def consumeSource(source:Source[ByteString,Any])(implicit materializer: akka.stream.Materializer, ec: ExecutionContext):Future[String] = {
    logger.debug("Consuming returned body")
    val sink = Sink.reduce((acc:ByteString, unit:ByteString)=>acc.concat(unit))
    val runnable = source.toMat(sink)(Keep.right)
    runnable.run().map(_.utf8String)
  }

  def request(uriPath:String,xmlString:String,headers:Map[String,String])
             (implicit materializer: akka.stream.Materializer,ec: ExecutionContext):
  Future[Either[HttpError,String]] = sendPut(uriPath, xmlString, headers).flatMap({ response=>
    response.body match {
      case Right(source)=>
        logger.debug("Send succeeded")
        consumeSource(source).map(data=>Right(data))
      case Left(errorString)=>
        VSError.fromXml(errorString) match {
          case Left(unparseableError)=>
            val errMsg = s"Send failed: ${response.code} - $errorString"
            logger.warn(errMsg)
            Future(Left(HttpError(errMsg, response.code)))
          case Right(vsError)=>
            logger.warn(vsError.toString)
            Future(Left(HttpError(vsError.toString, response.code)))
        }
    }})

  def requestGet(uriPath:String, headers:Map[String,String],queryParams:Map[String,String]=Map())
                (implicit materializer: akka.stream.Materializer,ec: ExecutionContext):
  Future[Either[HttpError,String]] = sendGet(uriPath, headers, queryParams).flatMap({ response=>
    response.body match {
      case Right(source)=>
        logger.debug("Send succeeded")
        consumeSource(source).map(data=>Right(data))
      case Left(errorString)=>
        VSError.fromXml(errorString) match {
          case Left(unparseableError)=>
            val errMsg = s"Send failed: ${response.code} - $errorString"
            logger.warn(errMsg)
            Future(Left(HttpError(errMsg, response.code)))
          case Right(vsError)=>
            logger.warn(vsError.toString)
            Future(Left(HttpError(vsError.toString, response.code)))
        }
    }})
}
