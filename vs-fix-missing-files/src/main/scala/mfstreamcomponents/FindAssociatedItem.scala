package mfstreamcomponents

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSEntry, VSFile, VSLazyItem}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Flow that looks up metadata for the Item assocaited with the given File, fi any.
  * @param comm implicitly provided VSCommunicator
  * @param mat implicitly provided akka Materializer
  */
class FindAssociatedItem (implicit comm:VSCommunicator, mat:Materializer) extends GraphStage[FlowShape[VSFile,VSEntry]]{
  private val in:Inlet[VSFile] = Inlet.create("FindAssociatedItem.in")
  private val out:Outlet[VSEntry] = Outlet.create("FindAssociatedItem.out")

  override def shape: FlowShape[VSFile, VSEntry] = FlowShape.of(in,out)

  val interestingFields = Seq("gnm_external_archive_request_external_archive_path","gnm_asset_status","gnm_asset_category","title","gnm_asset_path")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val completionCb = createAsyncCallback[VSEntry](entry=>push(out, entry))
        val errorCb = createAsyncCallback[Throwable](err=>failStage(err))

        val vsFile = grab(in)
        val maybeItem = vsFile.membership.map(m=>VSLazyItem(m.itemId))

        maybeItem match {
          case Some(vsItem)=>
            vsItem.getMoreMetadata(interestingFields).onComplete({
              case Failure(err)=>
                logger.error("getMoreMetadata crashed: ", err)
                errorCb.invoke(err)
              case Success(Left(err))=>
                logger.error(s"Could not get metadata from Vidispine: $err")
                errorCb.invoke(new RuntimeException(err.toString))
              case Success(Right(updatedItem))=>
                logger.debug(s"Got metadata for ${vsItem.itemId}: $updatedItem")
                completionCb.invoke(VSEntry(Some(vsFile),Some(updatedItem), None))
            })
          case None=>
            logger.warn(s"No file attachment for ${vsFile.vsid}, proceeding to next")
            pull(in)
        }
      }

    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
