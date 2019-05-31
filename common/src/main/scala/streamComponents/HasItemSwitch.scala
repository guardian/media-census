package streamComponents

import akka.stream.{Attributes, FanOutShape, Inlet, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, GraphStage, GraphStageLogic}
import models.{AssetSweeperFile, MediaCensusEntry}

class HasItemSwitch extends GraphStage[UniformFanOutShape[MediaCensusEntry, MediaCensusEntry]]{
  private final val in:Inlet[MediaCensusEntry] = Inlet.create("HasItemSwitch.in")
  private final val outYes:Outlet[MediaCensusEntry] = Outlet.create("HasItemSwitch.yes")
  private final val outNo:Outlet[MediaCensusEntry] = Outlet.create("HasItemSwitch.no")

  override def shape = new UniformFanOutShape[MediaCensusEntry, MediaCensusEntry](in, Array(outYes, outNo))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)
//        if(elem.)
      }
    })
  }
}
