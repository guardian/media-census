package streamcomponents

import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.UnclogStream
import org.slf4j.LoggerFactory
import scala.util.control.Breaks._

/**
  * Checks whether the incoming object contains a VidispineProject object set to sensitive.
  * If so, pushes it to the outYes port, if not pushes it to the outNo port.
  */
class SensitiveSwitch extends GraphStage[UniformFanOutShape[UnclogStream, UnclogStream]]{
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

        elem.ParentProjects.foreach ( process_projects => {
          if(process_projects.sensitive){
            push(outYes, elem)
            break
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
