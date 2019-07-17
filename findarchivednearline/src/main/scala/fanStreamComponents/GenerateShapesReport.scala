package fanStreamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.ItemShapeReport
import org.slf4j.LoggerFactory
import vidispine.VSLazyItem

class GenerateShapesReport (knownOnlineStorages:Seq[String], knownNearlineStorages:Seq[String]) extends GraphStage[FlowShape[VSLazyItem,ItemShapeReport]] {
  private val in:Inlet[VSLazyItem] = Inlet.create("GenerateShapesReport.in")
  private val out:Outlet[ItemShapeReport] = Outlet.create("GenerateShapesReport.out")


  override def shape: FlowShape[VSLazyItem, ItemShapeReport] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val vsitem = grab(in)

        val maybeOriginalShape = vsitem.shapes.flatMap(_.get("original"))

        //convert the metadata to a more simply readable map
        val meta = vsitem.lookedUpMetadata.map(tuple=>tuple._1->tuple._2.values.map(_.value)).toMap
        logger.debug(s"Got item ${vsitem.itemId}")
        meta.foreach(tuple=>logger.debug(s"\t${tuple._1}: ${tuple._2}"))
        vsitem.shapes.getOrElse(Map()).foreach(tuple=>logger.debug(s"\tShape ${tuple._1}: ${tuple._2}"))
        val report = maybeOriginalShape match {
          case None=>
            logger.debug(s"Item ${vsitem.itemId} has no original shape")
            ItemShapeReport(vsitem, noOriginalShape = true,0,0,0)
          case Some(originalShape)=>
            logger.info(s"Item ${vsitem.itemId} has original shape data $originalShape")
            val onlineStorageCount = originalShape.files.count(vsfile=>knownOnlineStorages.contains(vsfile.storage))
            val nearlineStorageCount = originalShape.files.count(vsfile=>knownNearlineStorages.contains(vsfile.storage))
            val otherStorageCount = originalShape.files.length - onlineStorageCount - nearlineStorageCount

            ItemShapeReport(vsitem, noOriginalShape = false, onlineStorageCount, nearlineStorageCount, otherStorageCount)
        }

        push(out, report)
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
