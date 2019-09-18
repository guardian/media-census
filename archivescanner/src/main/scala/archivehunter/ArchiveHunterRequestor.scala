package archivehunter

import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpHeader, HttpRequest}
import akka.stream.Materializer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.GenericHttpCredentials
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ArchiveHunterRequestor(baseUri:String, key:String)(implicit val system:ActorSystem, implicit val mat:Materializer) {
  private val logger = LoggerFactory.getLogger(getClass)

  private val secretKeySpec = new SecretKeySpec(key.getBytes, "HmacSHA256")

  def currentTimeString = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)

  def makeAuth(req:HttpRequest) = {
    /*
    def get_token(uri, secret):
    httpdate = formatdate(timeval=mktime(datetime.now().timetuple()),localtime=False,usegmt=True)
    url_parts = urlparse(uri)

    string_to_sign = "{0}\n{1}".format(httpdate, url_parts.path)
    print "string_to_sign: " + string_to_sign
    hm = hmac.new(secret, string_to_sign,hashlib.sha256)
    return "HMAC {0}".format(base64.b64encode(hm.digest())), httpdate
     */
    val httpDate = currentTimeString
    logger.debug(s"date string is $httpDate")
    val stringToSign = s"$httpDate\n${req.uri.path.toString()}"
    logger.debug(s"stringToSign is $stringToSign")

    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secretKeySpec)
    val resultBytes = mac.doFinal(stringToSign.getBytes)

    val signature = Base64.encodeBase64String(resultBytes)
    logger.debug(s"signature is $signature")
    val authHeader = headers.Authorization(GenericHttpCredentials("HMAC", signature))
    logger.debug(s"authHeader is ${authHeader.toString()}")
    val dateHeader = headers.RawHeader("Date",httpDate)
    logger.debug(s"dateHeader is ${dateHeader.toString()}")
    req.withHeaders(authHeader, dateHeader)
  }

  def lookupRequest(forPath:String):Future[Either[String,ArchiveHunterLookupResult]] = {
    val url = s"$baseUri/api/searchpath/${URLEncoder.encode(forPath)}"

    logger.debug(s"Going to request $url")

    val req = HttpRequest(uri = url)
    val signedRequest = makeAuth(req)
    Http().singleRequest(signedRequest).flatMap(response=>{
      response.status match {
        case StatusCodes.NotFound=>
          response.entity.discardBytes()
          Future(Right(ArchiveHunterNotFound))
        case StatusCodes.OK=>
          response.entity.getDataBytes().runWith(Sink.fold(ByteString())((acc,entry)=>acc++entry), mat).map(bytes=>{
            val stringData = bytes.decodeString("UTF-8")
            io.circe.parser.parse(stringData) match {
              case Right(parsedData)=>
                Right(ArchiveHunterFound("fixme","fixme"))
              case Left(parseError)=>Left(parseError.toString)
            }
          })
        case _=>
          response.entity.getDataBytes().runWith(Sink.fold(ByteString())((acc,entry)=>acc++entry), mat).map(bytes=>{
            (Left(s"Unexpected response ${response.status} from server: ${bytes.decodeString("UTF-8")}"))
          })
      }
    })
  }
}
