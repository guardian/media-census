package streamComponents

import akka.stream.{Attributes, Inlet, Materializer, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.MediaCensusEntry
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSFile}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * checks whether the given entry identifies an item that has a VSFile attached
  */
class VSFileSwitch (implicit communicator:VSCommunicator, mat:Materializer) extends GraphStage[UniformFanOutShape[MediaCensusEntry, MediaCensusEntry ]] {
  private final val in:Inlet[MediaCensusEntry] = Inlet.create("VSFileSwitch.in")
  private final val outYes:Outlet[MediaCensusEntry] = Outlet.create("VSFileSwitch.yes")
  private final val outNo:Outlet[MediaCensusEntry] = Outlet.create("VSFileSwitch.no")

  override def shape = new UniformFanOutShape[MediaCensusEntry, MediaCensusEntry](in,Array(outYes, outNo))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val completeCb = getAsyncCallback[MediaCensusEntry](finalElem=>{
          if(finalElem.vsFileId.isDefined){
            push(outYes, finalElem)
          } else {
            push(outNo, finalElem)
          }
        })

        val elem = grab(in)

        val sourceStorageId = elem.sourceStorage.get
        VSFile.forPathOnStorage(sourceStorageId, elem.storageSubpath.get).onComplete({
          case Failure(err)=>
            logger.error("VS lookup process crashed: ", err)
            failStage(err)
          case Success(Left(err))=>
            logger.error(s"Could not look up file in VS: ${err.toString}")
            failStage(new RuntimeException(err))
          case Success(Right(Some(vsFile)))=>
            logger.info(s"Found file for ${elem.storageSubpath} at ${vsFile.vsid}")
            val updatedElem = elem.copy(
              vsFileId = Some(vsFile.vsid),
              vsItemId = vsFile.membership.map(_.itemId),
              vsShapeIds = vsFile.membership.map(_.shapes.map(_.shapeId))
            )
            completeCb.invoke(updatedElem)
          case Success(Right(None))=>
            logger.info(s"No files present for ${elem.storageSubpath}")
            val updatedElem = elem.copy(
              vsFileId = None,
              vsItemId = None,
              vsShapeIds = None
            )
            completeCb.invoke(updatedElem)
        })
      }
    })

    setHandler(outYes, new AbstractOutHandler {
      override def onPull(): Unit = {
        if(!hasBeenPulled(in)) pull(in)
      }
    })

    setHandler(outNo, new AbstractOutHandler {
      override def onPull(): Unit = {
        if(!hasBeenPulled(in)) pull(in)
      }
    })
  }
}
