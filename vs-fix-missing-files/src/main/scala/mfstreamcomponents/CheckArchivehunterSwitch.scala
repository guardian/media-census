package mfstreamcomponents
import java.net.{URI, URLEncoder}

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.util.ByteString
import com.gu.hmac.HMACHeaders
import com.softwaremill.sttp._
import com.softwaremill.sttp.SttpBackendOptions.ProxyType.Http
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import helpers.ZonedDateTimeEncoder
import mfmodels.ArchiveHunterResponse
import org.slf4j.{Logger, LoggerFactory}
import vidispine.{FieldNames, VSEntry}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class CheckArchivehunterSwitch (archiveHunterBaseUri:String, override val secret:String)(implicit actorSystem:ActorSystem, mat:Materializer)
  extends GraphStage[UniformFanOutShape[VSEntry, VSEntry]] with HMACHeaders with ZonedDateTimeEncoder{
  private val outerLogger = LoggerFactory.getLogger(getClass)

  private val in:Inlet[VSEntry] = Inlet.create("CheckArchivehunterSwitch.in")
  private val yes:Outlet[VSEntry] = Outlet.create("CheckArchivehunterSwitch.yes")
  private val no:Outlet[VSEntry] = Outlet.create("CheckArchivehunterSwitch.no")

  protected implicit val sttpBackend:SttpBackend[Future,Source[ByteString, Any]] = AkkaHttpBackend.usingActorSystem(actorSystem)

  override def shape: UniformFanOutShape[VSEntry, VSEntry] = UniformFanOutShape(in, yes, no)

  def makeHmacHeaders(uri:String) = {
    createHMACHeaderValues(new URI(uri))
  }

  private def makeRequest(uri:String) = {
    val auth = createHMACHeaderValues(new URI(uri))

    outerLogger.info(s"Auth is ${auth.token} with date ${auth.date}")
    sttp
      .get(uri"$uri")
      .headers(Map("X-Gu-Tools-HMAC-Date"->auth.date, "X-Gu-Tools-HMAC-Token"->s"HMAC ${auth.token}"))
      .response(asStream[Source[ByteString,Any]])
      .send()
  }

  /**
    * internal method to buffer returned data into a String for parsing
    * @param source Akka source that yields ByteString entries
    * @return a Future, which contains the String of the returned content.
    */
  private def consumeSource(source:Source[ByteString,Any]):Future[String] = {
    outerLogger.debug("Consuming returned body")
    val sink = Sink.reduce((acc:ByteString, unit:ByteString)=>acc.concat(unit))
    val runnable = source.toMat(sink)(Keep.right)
    runnable.run().map(_.utf8String)
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    import io.circe.generic.auto._
    import io.circe.syntax._

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val yesCb = createAsyncCallback[VSEntry](entry=>push(yes,entry))
        val noCb = createAsyncCallback[VSEntry](entry=>push(no,entry))
        val failureCb = createAsyncCallback[Throwable](err=>failStage(err))

        val elem = grab(in)

        val maybeCollectionName = elem.vsItem.flatMap(_.getSingle(FieldNames.EXTERNAL_ARCHIVE_DEVICE))
        val maybeArchivePath  = elem.vsItem.flatMap(_.getSingle(FieldNames.EXTERNAL_ARCHIVE_PATH)).map(URLEncoder.encode(_,"UTF-8"))

        if(maybeCollectionName.isDefined && maybeArchivePath.isDefined) {
          val uri = s"https://$archiveHunterBaseUri/api/search/specific/${maybeCollectionName.get}?filePath=${maybeArchivePath.get}"
          logger.debug(s"URL is $uri")

          makeRequest(uri).map(response => {
            response.body match {
              case Left(err) =>
                logger.error(s"Could not make call to ArchiveHunter: $err")
                failureCb.invoke(new RuntimeException(err.toString))
              case Right(source) =>
                val contentFuture = consumeSource(source).map(io.circe.parser.parse)
                contentFuture.map({
                  case Left(parsingFailure)=>
                    logger.error(s"Could not parse json from server: $parsingFailure")
                    failureCb.invoke(new RuntimeException(parsingFailure.toString))
                  case Right(jsonContent)=>
                    jsonContent.as[ArchiveHunterResponse] match {
                      case Left(err)=>
                        logger.error(s"Could not unmarshal data from JSON: $err")
                        failureCb.invoke(new RuntimeException(err.toString))
                      case Right(marshalledData)=>
                        if(marshalledData.entryCount==0){
                          logger.warn(s"File ${maybeCollectionName.get}:${maybeArchivePath.get} is not known to Archive Hunter")
                          noCb.invoke(elem)
                        } else if(marshalledData.entryCount==1){
                          logger.info(s"File ${maybeCollectionName.get}:${maybeArchivePath.get} is unambigous")
                          if(marshalledData.entries.head.beenDeleted){
                            logger.warn(s"Archive Hunter has registered ${maybeCollectionName.get}:${maybeArchivePath.get} as deleted!")
                            noCb.invoke(elem)
                          } else {
                            yesCb.invoke(elem)
                          }
                        } else {
                          logger.warn(s"File ${maybeCollectionName.get}:${maybeArchivePath.get} has multiple matches, this should not happen")
                          marshalledData.entries.foreach(entry=>logger.warn(entry.toString))
                        }
                    }
                })
            }
          })
        } else {
          logger.warn(s"Item ${elem.vsItem.map(_.itemId)} does not have required archive fields set")
          noCb.invoke(elem)
        }
      }
    })

    setHandler(yes, new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    })

    setHandler(no, new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    })
  }
}
