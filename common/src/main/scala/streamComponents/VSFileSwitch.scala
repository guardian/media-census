package streamComponents

import akka.stream.{Attributes, Inlet, Materializer, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.MediaCensusEntry
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSFile, VSFileState}

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

    def findBestVsFile(vsFileSeq:Seq[VSFile]): VSFile = {
      if(vsFileSeq.length==1){
        vsFileSeq.head
      } else {
        val goodFilesStage1 = vsFileSeq.filter(_.membership.isDefined)
        val goodFilesStage2 = goodFilesStage1.filter(vsFile=>
          !vsFile.state.contains(VSFileState.LOST) &&
          !vsFile.state.contains(VSFileState.UNKNOWN) &&
          !vsFile.state.contains(VSFileState.MISSING) &&
          !vsFile.state.contains(VSFileState.TO_BE_DELETED)
        )
        logger.debug(s"findBestVsFile: got incoming $vsFileSeq")
        logger.debug(s"findBestVsFile: found membered files $goodFilesStage1")
        logger.debug(s"findBestVsFile: found good membered files $goodFilesStage2")

        goodFilesStage2.headOption match {
          case Some(vsFile)=>vsFile
          case None=>
            goodFilesStage1.headOption match {
              case Some(vsFile)=>vsFile
              case None=>
                logger.warn(s"No good files found from $vsFileSeq, returning first")
                vsFileSeq.head
            }
        }
      }
    }

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = try {
        onPushBody()
      } catch {
        case err:Throwable=>
          logger.error("Uncaught exception checking vs files: ", err)
          failStage(err)
      }

      def onPushBody(): Unit = {
        val completeCb = getAsyncCallback[MediaCensusEntry](finalElem=>{
          if(finalElem.vsFileId.isDefined){
            push(outYes, finalElem)
          } else {
            push(outNo, finalElem)
          }
        })

        val failCb = getAsyncCallback[Throwable](err=>failStage(err))

        val elem = grab(in)

        val sourceStorageId = elem.sourceStorage.get
        VSFile.forPathOnStorage(sourceStorageId, elem.storageSubpath.get).onComplete({
          case Failure(err)=>
            logger.error("VS lookup process crashed: ", err)
            failCb.invoke(err)
          case Success(Left(errList))=>
            logger.error(s"Could not look up file in VS: ${errList.toString}")
            failCb.invoke(new RuntimeException(errList.head))
          case Success(Right(vsFileSeq))=>
            logger.debug(s"Found file(s) for ${elem.storageSubpath} at ${vsFileSeq.map(_.vsid)}")
            val updatedElem = if(vsFileSeq.isEmpty){
              logger.debug(s"No files present for ${elem.storageSubpath}")
              elem.copy(
                vsFileId = None,
                vsItemId = None,
                vsShapeIds = None
              )
            } else {
              val bestVsFile = findBestVsFile(vsFileSeq)
              elem.copy(
                vsFileId = Some(bestVsFile.vsid),
                vsItemId = bestVsFile.membership.map(_.itemId),
                vsShapeIds = bestVsFile.membership.map(_.shapes.map(_.shapeId))
              )
            }
            completeCb.invoke(updatedElem)
        })
      }
    })

    setHandler(outYes, new AbstractOutHandler {
      override def onPull(): Unit = {
        if(!hasBeenPulled(in)){
          pull(in)
        } else {
          logger.warn("VSFileSwitch: input already pulled")
        }
      }
    })

    setHandler(outNo, new AbstractOutHandler {
      override def onPull(): Unit = {
        if(!hasBeenPulled(in)){
          pull(in)
        } else {
          logger.warn("VSFileSwitch: input already pulled")
        }
      }
    })
  }
}
