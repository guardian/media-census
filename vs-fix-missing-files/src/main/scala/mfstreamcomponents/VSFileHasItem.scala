package mfstreamcomponents

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import org.slf4j.LoggerFactory
import vidispine.VSFile

/**
  * pushes to either yes or no depending on whether the given VSFile has an item attached to it
  */
class VSFileHasItem extends GraphStage[UniformFanOutShape[VSFile, VSFile]]{
  private val in:Inlet[VSFile] = Inlet.create("VSFileHasItem.in")
  private val yes:Outlet[VSFile] = Outlet.create("VSFileHasItem.yes")
  private val no:Outlet[VSFile] = Outlet.create("VSFileHasItem.no")

  override def shape: UniformFanOutShape[VSFile, VSFile] = UniformFanOutShape(in,yes,no)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        if(elem.membership.isDefined){
          push(yes, elem)
        } else {
          push(no, elem)
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
