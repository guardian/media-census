package streamcomponents

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import models.{MediaStatusValue, UnclogStream}
import org.slf4j.LoggerFactory

/**
  * Sets flags based on settings in the input objects.
  */
class SetFlagsShape extends GraphStage[FlowShape[UnclogStream, UnclogStream]]{
  private final val in:Inlet[UnclogStream] = Inlet.create("SetFlagsShape.in")
  private final val out:Outlet[UnclogStream] = Outlet.create("SetFlagsShape.out")

  override def shape = new FlowShape[UnclogStream, UnclogStream](in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = try {
        onPushBody()
      } catch {
        case err:Throwable=>
          logger.error("Uncaught exception settings flags: ", err)
          failStage(err)
      }

      def onPushBody(): Unit = {
        val elem = grab(in)

        val deep_count = elem.ParentProjects.count(_.deepArchive)
        val killed_count = elem.ParentProjects.count(_.status.contains("Killed"))
        val sensitive_count = elem.ParentProjects.count(_.sensitive)
        val held_count = elem.ParentProjects.count(_.status.contains("Held"))
        val new_count = elem.ParentProjects.count(_.status.contains("New"))
        val title_production_count = elem.ParentProjects.count(_.status.contains("In Production"))
        val production_count = title_production_count + elem.ParentProjects.count(_.status.contains("In production"))
        val restored_count = elem.ParentProjects.count(_.status.contains("Restore"))
        val completed_count = elem.ParentProjects.count(_.status.contains("Completed"))
        val deletable_count = elem.ParentProjects.count(_.deletable)
        val deep_completed_count = elem.ParentProjects.filter(_.deepArchive).count(_.status.contains("Completed"))

        val updatedStatus = if(elem.ParentProjects.isEmpty) {
          MediaStatusValue.NO_PROJECT
        } else if(sensitive_count>0) {
          MediaStatusValue.SENSITIVE
        } else if(killed_count==elem.ParentProjects.length) {
          MediaStatusValue.DELETABLE
        } else if(held_count>0) {
          MediaStatusValue.PROJECT_HELD
        } else if(new_count>0) {
          MediaStatusValue.PROJECT_OPEN
        } else if(production_count>0) {
          MediaStatusValue.PROJECT_OPEN
        } else if(restored_count>0) {
          MediaStatusValue.PROJECT_OPEN
        } else if(completed_count==elem.ParentProjects.length) {
          if (deletable_count==elem.ParentProjects.length) {
            MediaStatusValue.DELETABLE
          } else if(deep_count>0) {
            MediaStatusValue.SHOULD_BE_ARCHIVED_AND_DELETED
          } else {
            MediaStatusValue.UNKNOWN
          }
        } else if(deep_completed_count>0) {
          if (deletable_count==elem.ParentProjects.length) {
            MediaStatusValue.DELETABLE
          } else {
            MediaStatusValue.SHOULD_BE_ARCHIVED
          }
        } else {
          MediaStatusValue.UNKNOWN
        }

        val updatedElem = elem.copy(MediaStatus = Some(updatedStatus))
        push(out, updatedElem)
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        if(!hasBeenPulled(in)) pull(in)
      }
    })
  }
}
