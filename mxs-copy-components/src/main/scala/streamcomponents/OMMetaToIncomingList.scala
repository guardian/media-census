package streamcomponents

import java.time.ZonedDateTime

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.{IncomingListEntry, ObjectMatrixEntry}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class OMMetaToIncomingList (implicit mat:Materializer, ec:ExecutionContext) extends GraphStage[FlowShape[ObjectMatrixEntry, IncomingListEntry]] {
  private val in:Inlet[ObjectMatrixEntry] = Inlet.create("OMMetaToIncomingList.in")
  private val out:Outlet[IncomingListEntry] = Outlet.create("OMMetaToIncomingList.out")

  override def shape: FlowShape[ObjectMatrixEntry, IncomingListEntry] = FlowShape.of(in, out)

  def splitFilePath(completePath:String) = {
    val splitter = "^(.*)/([^/]+)$".r

    val splitter(path,name) = completePath
    (path, name)
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val successCb = createAsyncCallback[IncomingListEntry](entry=>{
          push(out,entry)
        })
        val failureCb = createAsyncCallback[Throwable](err=>failStage(err))

        val elem = grab(in)
        elem.getMetadata.onComplete({
          case Success(updatedEntry)=>
//            println(updatedEntry.attributes.map(_.longValues))
//            println(updatedEntry.attributes.map(_.stringValues))
//            println(updatedEntry.attributes.map(_.intValues))
//            println(updatedEntry.attributes.map(_.boolValues))

            val path,name = updatedEntry.stringAttribute("MXFS_FILENAME").getOrElse("unknown/unknown")
            val output = new IncomingListEntry(
              path,
              name,
              updatedEntry.timeAttribute("MXFS_MODIFICATION_TIME").getOrElse(ZonedDateTime.now()),
              updatedEntry.longAttribute("DPSP_SIZE").getOrElse(-1)
            )
            successCb.invoke(output)

          case Failure(err)=>
            logger.error("Could not update metadata: ", err)
            failureCb.invoke(err)
        })
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}
