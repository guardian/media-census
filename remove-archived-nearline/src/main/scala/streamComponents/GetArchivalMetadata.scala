package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import vidispine.{ArchivalMetadata, VSCommunicator, VSLazyItem}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * looks up archival metadata for the given item
  */
class GetArchivalMetadata(implicit vsComm:VSCommunicator, mat:Materializer) extends GraphStage[FlowShape[Option[VSLazyItem], Option[ArchivalMetadata]]] {
  private val in:Inlet[Option[VSLazyItem]] = Inlet.create("GetArchivalMetadata.in")
  private val out:Outlet[Option[ArchivalMetadata]] = Outlet.create("GetArchivalMetadata.out")

  override def shape = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val item = grab(in)

        if(item.isEmpty){
          push(out, None)
        } else {
          val successCb = createAsyncCallback[ArchivalMetadata](meta => push(out, meta))
          val failureCb = createAsyncCallback[Throwable](err => failStage(err))

          ArchivalMetadata.fromLazyItem(item.get, alwaysFetch = false).onComplete({
            case Success(Right(content)) => successCb.invoke(content)
            case Success(Left(lookupErr)) =>
              logger.error(s"Could not look up metadata for ${item.get.itemId}: $lookupErr")
              failureCb.invoke(new RuntimeException("Vidispine lookup failed"))
            case Failure(err) =>
              logger.error(s"Failed to look up metadata for ${item.get.itemId}: ", err)
              failureCb.invoke(err)
          })
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
