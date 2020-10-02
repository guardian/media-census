package streamComponents

import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import com.gu.vidispineakka.vidispine.VSFile

class IsArchivedSwitch extends GraphStage[UniformFanOutShape[VSFile, VSFile]]{
  private val logger = LoggerFactory.getLogger(getClass)
  private final val in:Inlet[VSFile] = Inlet.create("IsArchivedSwitch.in")
  private final val yes:Outlet[VSFile] = Outlet.create("IsArchivedSwitch.yes")
  private final val no:Outlet[VSFile] = Outlet.create("IsArchivedSwitch.no")
  private final val conflict:Outlet[VSFile] = Outlet.create("IsArchivedSwitch.conflict")

  override def shape: UniformFanOutShape[VSFile, VSFile] = new UniformFanOutShape(in, Array(yes,no, conflict))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        if(elem.archiveHunterId.isDefined) {
          if(elem.archiveConflict.isEmpty || elem.archiveConflict.contains(false)) {
            push(yes, elem)
          } else {
            logger.warn(s"${elem.uri} (${elem.vsid}) has an archive conflict, not continuing")
            push(conflict, elem)
          }
        } else {
          push(no, elem)
        }
      }
    })

    setHandler(yes, new AbstractOutHandler {
      override def onPull(): Unit = if(isAvailable(in)) pull(in)
    })

    setHandler(no, new AbstractOutHandler {
      override def onPull(): Unit = if(isAvailable(in)) pull(in)
    })
  }
}
