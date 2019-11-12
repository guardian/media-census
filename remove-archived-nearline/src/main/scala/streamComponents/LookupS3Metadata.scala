package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.amazonaws.services.s3.AmazonS3
import models.ArchivedItemRecord
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

class LookupS3Metadata (client:AmazonS3) extends GraphStage[FlowShape[ArchivedItemRecord, ArchivedItemRecord]] {
  private val in:Inlet[ArchivedItemRecord] = Inlet.create("LookupS3Metadata.in")
  private val out:Outlet[ArchivedItemRecord] = Outlet.create("LookupS3Metadata.out")

  override def shape: FlowShape[ArchivedItemRecord, ArchivedItemRecord] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)
        val maybeMeta = Try { client.getObjectMetadata(elem.s3Bucket, elem.s3Path) }

        maybeMeta match {
          case Success(objectMeta)=>
            val result = elem.copy(s3Meta = Some(objectMeta))
            push(out, result)
          case Failure(err)=>
            logger.error(s"Could not look up s3://${elem.s3Bucket}/${elem.s3Path}: ", err)
            failStage(err)
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
