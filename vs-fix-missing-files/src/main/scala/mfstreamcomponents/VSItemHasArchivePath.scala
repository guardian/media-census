package mfstreamcomponents

import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import mfmodels.ExternalArchiveData
import org.slf4j.LoggerFactory
import vidispine.VSEntry

class VSItemHasArchivePath extends GraphStage[UniformFanOutShape[VSEntry, VSEntry]]{
  private final val in:Inlet[VSEntry] = Inlet.create("VSItemHasArchivePath.in")
  private final val yes:Outlet[VSEntry] = Outlet.create("VSItemHasArchivePath.yes")
  private final val no:Outlet[VSEntry] = Outlet.create("VSItemHasArchivePath.no")

  override def shape: UniformFanOutShape[VSEntry, VSEntry] = UniformFanOutShape(in, yes, no)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val data = elem.vsItem.map(item=>ExternalArchiveData.fromLazyItem(item))

        data match {
          case Some(externalArchiveData)=>
            logger.debug(s"Item ${elem.vsItem.get.itemId} has $externalArchiveData")
            if(externalArchiveData.archivePath.isDefined && externalArchiveData.archivePath.get !="" &&
            externalArchiveData.archiveDevice.isDefined && externalArchiveData.archiveDevice.get !=""){
              logger.debug(s"Item ${elem.vsItem.get.itemId} has external archive paths set")
              push(yes, elem)
            } else {
              logger.debug(s"Item ${elem.vsItem.get.itemId} has no external archive data set")
              push(no, elem)
            }
          case None=>
            logger.debug(s"Item had no external archive data")
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
