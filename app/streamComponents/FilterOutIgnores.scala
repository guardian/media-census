package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.AssetSweeperFile

class FilterOutIgnores extends GraphStage[FlowShape[AssetSweeperFile,AssetSweeperFile]] {
  private final val in:Inlet[AssetSweeperFile] = Inlet.create("FilterOutIgnores.in")
  private final val out:Outlet[AssetSweeperFile] = Outlet.create("FilterOutIgnores.out")

  override def shape: FlowShape[AssetSweeperFile, AssetSweeperFile] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem=grab(in)
        if(elem.ignore) pull(in) else push(out,elem)
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}
