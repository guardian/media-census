package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.gu.vidispineakka.vidispine.{VSFile, VSFileState}

/**
 * filters out VSFile entries that have statuses indicating a file problem
 */
class ValidStatusSwitch extends GraphStage[UniformFanOutShape[VSFile, VSFile]]{
  private final val in:Inlet[VSFile] = Inlet.create("ValidStatusSwitch.in")
  private final val yes:Outlet[VSFile] = Outlet.create("ValidStatusSwitch.yes")
  private final val no:Outlet[VSFile] = Outlet.create("ValidStatusSwitch.no")

  override def shape: UniformFanOutShape[VSFile, VSFile] = new UniformFanOutShape[VSFile, VSFile](in, Array(yes, no))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        elem.state match {
          case None=>
            push(no, elem)
          case Some(VSFileState.LOST)=>
            push(no, elem)
          case Some(VSFileState.MISSING)=>
            push(no, elem)
          case Some(VSFileState.TO_APPEAR)=>
            push(no, elem)
          case Some(VSFileState.UNKNOWN)=>
            push(no, elem)
          case _=>
            push(yes, elem)
        }
      }
    })

    private val outputHandler = new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    }

    setHandler(yes, outputHandler)
    setHandler(no, outputHandler)
  }
}
