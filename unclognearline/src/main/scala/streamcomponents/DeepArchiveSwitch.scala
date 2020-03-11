package streamcomponents

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import models.UnclogStream
import org.slf4j.LoggerFactory

/**
  * Checks whether the incoming object contains a VidispineProject object set to deep archive.
  * If so, pushes it to the outYes port, if not pushes it to the outNo port.
  */
class DeepArchiveSwitch extends GraphStage[UniformFanOutShape[UnclogStream, UnclogStream]]{
  private final val in:Inlet[UnclogStream] = Inlet.create("DeepArchiveSwitch.in")
  private final val outYes:Outlet[UnclogStream] = Outlet.create("DeepArchiveSwitch.yes")
  private final val outNo:Outlet[UnclogStream] = Outlet.create("DeepArchiveSwitch.no")

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

        elem.ParentProjects.foreach ( process_projects => {
          if(process_projects.deepArchive){
            push(outYes, elem)
            return
          }
        })

        push(outNo, elem)
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
