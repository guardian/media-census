package streamcomponents

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import models.UnclogStream
import org.slf4j.LoggerFactory

/**
  * Checks whether the incoming object contains a VSLazyItem object with more than fifteen projects listed.
  * If so, pushes it to the outYes port, if not pushes it to the outNo port.
  */
class ProjectCountSwitch extends GraphStage[UniformFanOutShape[UnclogStream, UnclogStream]]{
  private final val in:Inlet[UnclogStream] = Inlet.create("ProjectCount.in")
  private final val outYes:Outlet[UnclogStream] = Outlet.create("ProjectCount.yes")
  private final val outNo:Outlet[UnclogStream] = Outlet.create("ProjectCount.no")

  override def shape = new UniformFanOutShape[UnclogStream, UnclogStream](in, Array(outYes, outNo))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = try {
        onPushBody()
      } catch {
        case err:Throwable=>
          logger.error("Uncaught exception checking project count: ", err)
          failStage(err)
      }

      def onPushBody(): Unit = {
        val elem = grab(in)

        val maybeCollectionList = elem.VSItem.flatMap(_.get("__collection"))

        maybeCollectionList match {
          case Some(sequenceOfProjects)=>
            if (sequenceOfProjects.size > 15) {
              push (outYes, elem)
            } else {
              push (outNo, elem)
            }
          case None=>
            logger.warn(s"File ${elem.VSFile.vsid} either has no collections or no item!")
            push(outNo, elem)
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
