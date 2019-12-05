package streamComponents

import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import vidispine.VSFile

/**
  * pushes the provided VSFile to "yes" if its ID is in the string list provided at construction or pushes it to
  * "no" if it isn't
  * @param fileIdList a sequence of strings to match against
  */
class VSFileIdInList(fileIdList:Seq[String]) extends GraphStage[UniformFanOutShape[VSFile, VSFile]] {
  private final val in:Inlet[VSFile] = Inlet.create("VSFileIdInList.in")
  private final val yes:Outlet[VSFile] = Outlet.create("VSFileIdInList.yes")
  private final val no:Outlet[VSFile] = Outlet.create("VSFileIdInList.no")

  override def shape: UniformFanOutShape[VSFile, VSFile] = new UniformFanOutShape[VSFile, VSFile](in, Array(yes,no))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger:org.slf4j.Logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        if(fileIdList.contains(elem.vsid)){
          logger.debug(s"File ${elem.vsid} (${elem.path}) exists in match list")
          push(yes, elem)
        } else {
          logger.debug(s"File ${elem.vsid} (${elem.path}) does not exist in match list")
          push(no, elem)
        }
      }
    })

    val genericOutHandler = new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    }

    setHandler(yes, genericOutHandler)
    setHandler(no, genericOutHandler)
  }
}
