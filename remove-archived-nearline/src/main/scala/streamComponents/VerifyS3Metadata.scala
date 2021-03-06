package streamComponents

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import models.ArchivedItemRecord
import org.slf4j.LoggerFactory

/**
  * checks if the ObjectMetadata in the provided record matches with the VSFile metadata. If so, then mark no conflict and remove
  */
class VerifyS3Metadata extends GraphStage[UniformFanOutShape[ArchivedItemRecord,ArchivedItemRecord]] {
  private final val in:Inlet[ArchivedItemRecord] = Inlet.create("VerifyS3Metadata.in")
  private final val yes:Outlet[ArchivedItemRecord] = Outlet.create("VerifyS3Metadata.yes")
  private final val no:Outlet[ArchivedItemRecord] = Outlet.create("VerifyS3Metadata.no")

  override def shape = new UniformFanOutShape[ArchivedItemRecord,ArchivedItemRecord](in,Array(yes,no))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        elem.s3Meta match {
          case None=>
            failStage(new RuntimeException("Can't verify metadata on a record that does not have it"))
          case Some(meta)=>
            val sizeMatch = elem.nearlineItem.size==meta.getContentLength
            logger.info(s"verifyMetadata for ${elem.s3Bucket}/${elem.s3Path}: sizeMatch is $sizeMatch")

            val checksumMatch = if(meta.getContentMD5!=null && elem.nearlineItem.hash.isDefined) elem.nearlineItem.hash.get == meta.getContentMD5 else true
            logger.info(s"verifyMetadata for ${elem.s3Bucket}/${elem.s3Path}: checksumMatch is $sizeMatch")

            if(! sizeMatch || ! checksumMatch) {
              logger.warn(s"Could not verify the file ${elem.nearlineItem.uri} at s3://${elem.s3Bucket}/${elem.s3Path}: size match $sizeMatch checksum match $checksumMatch")
              val updated = elem.copy(nearlineItem=elem.nearlineItem.copy(archiveConflict=Some(true)))
              push(no, updated)
            } else {
              logger.info(s"Verified file ${elem.nearlineItem.vsid} at s3://${elem.s3Bucket}/${elem.s3Path}")
              val updated = elem.copy(nearlineItem = elem.nearlineItem.copy(archiveConflict = Some(false)))
              push(yes, updated)
            }
        }

      }
    })

    val genericPull = new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    }

    setHandler(yes, genericPull)
    setHandler(no, genericPull)
  }
}
