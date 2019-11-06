package archivehunter

import java.net.URI

import akka.actor.ActorSystem
import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.{ArchiveNearlineEntry, MediaCensusEntry}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * graph stage to look up the provided entry in Archive Hunter
  * @param baseUri base URL at which ArchiveHunter is available
  * @param key shared secret key that allows server->server communication with AH
  * @param system implicitly provided Actor System
  * @param mat implicitly provided Materializer
  */
class ArchiveHunterLookup(baseUri:String, key:String)(implicit val system:ActorSystem, implicit val mat:Materializer) extends GraphStage[FlowShape[ArchiveNearlineEntry, ArchiveNearlineEntry ]]{
  private final val in:Inlet[ArchiveNearlineEntry] = Inlet.create("ArchiveHunterLookup.in")
  private final val out:Outlet[ArchiveNearlineEntry] = Outlet.create("ArchiveHunterLookup.out")

  override def shape: FlowShape[ArchiveNearlineEntry, ArchiveNearlineEntry] = FlowShape.of(in,out)

  private val requestor = new ArchiveHunterRequestor(baseUri, key)

  def extractPathPart(ommsUrl:URI) = {
    val pathParts = ommsUrl.getPath.split("/")
    pathParts.drop(3).mkString("/")
  }
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        logger.debug(s"ArchiveHunterLookup - checking ${elem.omUri}")
        val ignoreCb = createAsyncCallback[Unit](_=>pull(in))
        val completedCb = createAsyncCallback[ArchiveNearlineEntry](entry=>push(out, entry))
        val failedCb = createAsyncCallback[Throwable](err=>failStage(err))

        Try { new URI(elem.omUri) } match {
          case Failure(err)=>
            logger.error(s"Got invalid data from source, could not parse URI ${elem.omUri}: ", err)
            pull(in)
          case Success(parsedUri)=>
            val lookup = extractPathPart(parsedUri)
            logger.info(s"Looking up $lookup")
            requestor.lookupRequest(lookup).onComplete({
              case Failure(err)=>
                logger.error("ArchiveHunter lookup crashed: ", err)
                failedCb.invoke(err)
              case Success(Left(err))=>
                logger.error(s"ArchiveHunter returned an error: $err")
                failedCb.invoke(new RuntimeException("ArchiveHunter returned an error. Consult logs for details."))
              case Success(Right(ArchiveHunterNotFound))=>
                logger.info(s"Item is not present in ArchiveHunter")
                ignoreCb.invoke(())
              case Success(Right(found:ArchiveHunterFound))=>
                logger.info(s"Item found in archivehunter at ${found.archiveHunterCollection} with ID ${found.archiveHunterId}")
                val updatedElem = elem.copy(archiveHunterId = Some(found.archiveHunterId), archiveHunterCollection = Some(found.archiveHunterCollection))
                completedCb.invoke(updatedElem)
            })
        }

      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
