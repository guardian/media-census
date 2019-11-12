package streamComponents

import akka.stream.{Attributes, FanInShape, FanInShape2, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.ArchivedItemRecord
import org.slf4j.LoggerFactory
import vidispine.{ArchivalMetadata, VSFile}

/**
  * custom merge component that sticks some ArchivalMetadata back into the given ArchivedItemRecord
  */
class PostLookupMerge extends GraphStage[FanInShape2[Option[ArchivalMetadata], ArchivedItemRecord, ArchivedItemRecord]] {
  private val vsMetaIn:Inlet[Option[ArchivalMetadata]] = Inlet.create("PostLookupMerge.vsFileIn")
  private val itemRecordIn:Inlet[ArchivedItemRecord] = Inlet.create("PostLookupMerge.itemRecordIn")
  private val out:Outlet[ArchivedItemRecord] = Outlet.create("PostLookupMerge.out")

  override def shape = new FanInShape2(vsMetaIn, itemRecordIn, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    private var vsMeta:Option[ArchivalMetadata] = None
    private var itemRecord:Option[ArchivedItemRecord] = None

    def makePush() = {
      push(out, itemRecord.get.copy(vsMeta=vsMeta))
      vsMeta = None
      itemRecord = None
    }

    setHandler(vsMetaIn, new AbstractInHandler {
      override def onPush(): Unit = {
        if(vsMeta.isDefined){
          logger.error("Stream error, tried to set new metadata when there is some there already")
          failStage(new RuntimeException("Metadata already existed"))
        } else {
          vsMeta = grab(vsMetaIn)
          if(itemRecord.isDefined) makePush()
        }
      }
    })

    setHandler(itemRecordIn, new AbstractInHandler {
      override def onPush(): Unit = {
        if(itemRecord.isDefined){
          logger.error("Stream error, tried to set new item record when there is on there already")
          failStage(new RuntimeException("Item already existed"))
        } else {
          itemRecord = Some(grab(itemRecordIn))
          if(vsMeta.isDefined) makePush()
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        pull(vsMetaIn)
        pull(itemRecordIn)
      }
    })
  }
}
