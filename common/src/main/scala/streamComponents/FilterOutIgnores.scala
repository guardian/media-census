package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.{AssetSweeperFile, MediaCensusEntry}
import org.slf4j.LoggerFactory

class FilterOutIgnores extends GraphStage[FlowShape[MediaCensusEntry,MediaCensusEntry]] {
  private final val in:Inlet[MediaCensusEntry] = Inlet.create("FilterOutIgnores.in")
  private final val out:Outlet[MediaCensusEntry] = Outlet.create("FilterOutIgnores.out")

  override def shape: FlowShape[MediaCensusEntry, MediaCensusEntry] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private def logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = try {
        onPushBody()
      } catch {
        case err:Throwable=>
          logger.error("Uncaught exception checking vs files: ", err)
          failStage(err)
      }

      def onPushBody(): Unit = {
        val elem=grab(in)
        if(elem.originalSource.ignore) pull(in) else push(out,elem)
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}
