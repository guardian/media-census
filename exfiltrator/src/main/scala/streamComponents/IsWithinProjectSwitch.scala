package streamComponents

import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory

/**
 * pushes the incoming element to the "YES" port if its a member of any of the collections identified and to the "NO"
 * port if it's not
 * @param projectIdsToMatch if the item has a '__collection' field identifying any of these then it is sent to "YES"
 */
class IsWithinProjectSwitch(projectIdsToMatch:Seq[String], description:String = "these projects") extends GraphStage[UniformFanOutShape[ExfiltratorStreamElement, ExfiltratorStreamElement]] {
  private final val in:Inlet[ExfiltratorStreamElement] = Inlet.create("IsSensitiveProjectSwitch.in")
  private final val yes:Outlet[ExfiltratorStreamElement] = Outlet.create("IsSensitiveProjectSwitch.yes")
  private final val no:Outlet[ExfiltratorStreamElement] = Outlet.create("IsSensitiveProjectSwitch.no")

  override def shape: UniformFanOutShape[ExfiltratorStreamElement, ExfiltratorStreamElement] = new UniformFanOutShape[ExfiltratorStreamElement, ExfiltratorStreamElement](in, Array(yes, no))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        elem.maybeItem match {
          case None=>
            logger.info(s"File ${elem.file.vsid} (${elem.file.path}) has no item, assuming that it is not sensitive")
            push(no, elem)
          case Some(item)=>
            item.get("__collection") match {
              case None=>
                logger.warn(s"Item ${item.itemId} does not have any collections, this may indicate a problem")
                push(no, elem)
              case Some(collectionIds)=>
                val matchingIds = collectionIds.intersect(projectIdsToMatch)
                if(matchingIds.nonEmpty) {
                  logger.info(s"Item ${item.itemId} is a member of these $description, leaving it: $matchingIds")
                  push(yes, elem)
                } else {
                  logger.debug(s"Item ${item.itemId} is not part of a sensitive project")
                  push(no, elem)
                }
            }
        }
      }
    })

    val commonOutHandler = new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    }

    setHandler(yes, commonOutHandler)
    setHandler(no, commonOutHandler)
  }
}
