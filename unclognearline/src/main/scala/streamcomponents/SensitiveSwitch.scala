package streamcomponents

import akka.stream.{Attributes, FanOutShape, Inlet, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.UnclogStream
import org.slf4j.LoggerFactory

/**
  * checks whether the incoming census entry identifies a file that corresponds to a filepath known by VS.
  * if so, pushes it to the outYes port, if not pushes it to the outNo port
  * @param vsPathMap
  */
class SensitiveSwitch() extends GraphStage[UniformFanOutShape[UnclogStream, UnclogStream]]{
  private final val in:Inlet[UnclogStream] = Inlet.create("SensitiveSwitch.in")
  private final val outYes:Outlet[UnclogStream] = Outlet.create("SensitiveSwitch.yes")
  private final val outNo:Outlet[UnclogStream] = Outlet.create("SensitiveSwitch.no")

  override def shape = new UniformFanOutShape[UnclogStream, UnclogStream](in, Array(outYes, outNo))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = try {
        onPushBody()
      } catch {
        case err:Throwable=>
          logger.error("Uncaught exception checking sensitive: ", err)
          failStage(err)
      }

      def onPushBody(): Unit = {
        val elem = grab(in)

        //filter out tuples from the map where the element's filepath starts with the map's key
        val matchingElements = vsPathMap.filter(tuple=>elem.originalSource.filepath.startsWith(tuple._1))
        if(matchingElements.isEmpty){
          logger.info(s"Could not find item ${elem.originalSource.filepath}/${elem.originalSource.filename} on any known storage")
          push(outNo, elem)
        } else {
          val updatedElem = elem.copy(
            sourceStorage = Some(matchingElements.head._2.vsid),
            storageSubpath = VSFile.storageSubpath(elem.originalSource.filepath + "/" + elem.originalSource.filename, matchingElements.head._1)
          )
          logger.info(s"Found item on storage ${updatedElem.sourceStorage} at relative path ${updatedElem.storageSubpath}")
          push(outYes, updatedElem)
        }
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
