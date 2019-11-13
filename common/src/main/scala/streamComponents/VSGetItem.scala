package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSFile, VSLazyItem}

import scala.concurrent.ExecutionContext

class VSGetItem(fieldList:Seq[String])(implicit comm:VSCommunicator, mat:Materializer, ec:ExecutionContext) extends GraphStage[FlowShape[VSFile, (VSFile, Option[VSLazyItem])]] {
  private final val in:Inlet[VSFile] = Inlet.create("VSGetItem.in")
  private final val out:Outlet[(VSFile, Option[VSLazyItem])] = Outlet.create("VSGetItem.out")

  override def shape = FlowShape.of(in, out)

  /**
    * create a new VSLazyItem. Included like this to make testing easier.
    * @param itemId
    * @return
    */
  def makeItem(itemId:String) = VSLazyItem(itemId)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    val completedCb = createAsyncCallback[(VSFile, Option[VSLazyItem])](itm=>push(out,(itm._1,itm._2)))
    val failedCb = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        elem.membership match {
          case Some(fileItemMembership)=>
            val item = makeItem(fileItemMembership.itemId)

            item.getMoreMetadata(fieldList).map({
              case Left(metadataError)=>
                logger.error(s"Could not get metadata for item ${fileItemMembership.itemId}: $metadataError")
                failedCb.invoke(new RuntimeException("Could not lookup metadata"))
              case Right(updatedItem)=>
                logger.debug(s"Looked up metadata for item ${updatedItem.itemId}")
                completedCb.invoke((elem, Some(updatedItem)))
            })
          case None=>
            logger.warn(s"Can't look up item metadata for file ${elem.vsid} as it is not a member of any item")
            completedCb.invoke((elem, None))
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
