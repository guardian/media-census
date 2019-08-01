package fomStreamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import vidispine.VSFile

/**
  * simple flow stage that warns and filters out if the VSFile has an item membership.
  */
class NoMembershipFilter extends GraphStage[FlowShape[VSFile, VSFile ]] {
  private val in:Inlet[VSFile] = Inlet.create("NoMembershipFilter.in")
  private val out:Outlet[VSFile] = Outlet.create("NoMembershipFilter.out")

  override def shape: FlowShape[VSFile, VSFile] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        if(elem.membership.isDefined){
          logger.warn(s"File ${elem.vsid} (${elem.path} on ${elem.storage}) appeared in search but has item membership: ${elem.membership}")
          pull(in)
        } else {
          push(out,elem)
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
