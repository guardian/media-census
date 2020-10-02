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

        logger.info(s"isArchivedSwitch received ${elem.vsid}")
        if(elem.archiveHunterId.isDefined) {
          if(elem.archiveConflict.isEmpty || elem.archiveConflict.contains(false)) {
            logger.info(s"${elem.vsid} is archived without conflict")
            push(yes, elem)
          } else {
            logger.warn(s"${elem.uri} (${elem.vsid}) has an archive conflict, not continuing")
            push(conflict, elem)
          }
        } else {
          logger.info(s"${elem.vsid} is not archived")
          push(no, elem)
        }
      }
    })

    val defaultOutHandler = new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    }

    setHandler(yes, defaultOutHandler)

    setHandler(no, defaultOutHandler)

    setHandler(conflict, defaultOutHandler)
  }
}
