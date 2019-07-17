package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import vidispine.{FieldNames, VSLazyItem}

/**
  * drops the item from the stream if the metadata does not support it being "archived".
  */
class ItemArchivedCheck extends GraphStage[FlowShape[VSLazyItem, VSLazyItem ]] {
  private val in:Inlet[VSLazyItem] = Inlet.create("ItemArchivedCheck.in")
  private val out:Outlet[VSLazyItem] = Outlet.create("ItemArchivedCheck.out")

  override def shape: FlowShape[VSLazyItem, VSLazyItem] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val item = grab(in)

        val archive_status = item.getSingle(FieldNames.EXTERNAL_ARCHIVE_STATUS)
        val archive_request = item.getSingle(FieldNames.EXTERNAL_ARCHIVE_REQUEST)
        val archive_path = item.getSingle(FieldNames.EXTERNAL_ARCHIVE_PATH)
        val archive_device = item.getSingle(FieldNames.EXTERNAL_ARCHIVE_DEVICE)

        if(archive_path.isEmpty || archive_device.isEmpty){
          logger.info(s"Item ${item.itemId} is not archived, has no archive path/device")
          pull(in)
        } else if(archive_path.get=="" || archive_device.get==""){
          logger.info(s"Item ${item.itemId} is not archived, archive_path or archive_device are blank")
          pull(in)
        } else {
          logger.info(s"Item ${item.itemId} is archived: $archive_status/$archive_request on $archive_device:$archive_path")
          push(out,item)
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
