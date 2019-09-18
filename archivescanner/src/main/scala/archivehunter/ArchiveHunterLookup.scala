package archivehunter

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic}
import models.MediaCensusEntry
import org.slf4j.LoggerFactory

class ArchiveHunterLookup extends GraphStage[FlowShape[MediaCensusEntry, MediaCensusEntry ]]{
  private final val in:Inlet[MediaCensusEntry] = Inlet.create("ArchiveHunterLookup.in")
  private final val out:Outlet[MediaCensusEntry] = Outlet.create("ArchiveHunterLookup.out")

  override def shape: FlowShape[MediaCensusEntry, MediaCensusEntry] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)


  }
}
