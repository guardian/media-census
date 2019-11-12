package streamComponents

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.ArchivedItemRecord
import org.slf4j.LoggerFactory
import vidispine.{ArchivalMetadata, VSCommunicator}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class FixVidispineMeta (implicit vsComm:VSCommunicator, mat:Materializer) extends GraphStage[FlowShape[ArchivedItemRecord,ArchivedItemRecord]] {
  private val in:Inlet[ArchivedItemRecord] = Inlet.create("FixVidispineMeta.in")
  private val out:Outlet[ArchivedItemRecord] = Outlet.create("FixVidispineMeta.out")

  override def shape: FlowShape[ArchivedItemRecord, ArchivedItemRecord] = FlowShape.of(in, out)

  /**
    * check if the metadata needs to be overwritten
    *
    * @param meta
    * @param s3Bucket
    * @param s3Path
    * @return a 2-tuple of boolean values. The first indicates whether the metadata needs writing (true=>needs writing). The second indicates whether the
    *         s3 path is set correctly (true=>yes).
    */
  def needsFix(meta:Option[ArchivalMetadata], s3Bucket:String, s3Path:String):(Boolean,Boolean) = {
    if(meta.isEmpty){
      (true, true)
    } else {
      (meta.get.externalArchiveRequest!=ArchivalMetadata.AR_NONE ||
      meta.get.externalArchiveStatus!=ArchivalMetadata.AS_ARCHIVED ||
      !meta.get.externalArchiveDeleteShape,
        meta.get.externalArchiveDevice==s3Bucket ||
        meta.get.externalArchivePath==s3Path
        )
    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    val successCb = createAsyncCallback[ArchivedItemRecord](rec=>push(out,rec))
    val failedCb = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        if(elem.nearlineItem.membership.isEmpty){
          logger.warn(s"No item found for file ${elem.nearlineItem.vsid} ( ${elem.s3Bucket}/${elem.s3Path}), can't update")
          push(out, elem)
        } else {
          val archivalMetaIncoming = elem.vsMeta.get

          val (shouldWrite,correctPath) = needsFix(elem.vsMeta, elem.s3Bucket, elem.s3Path)
          if(!correctPath){
            logger.error(s"Vidispine item for file ${elem.nearlineItem.vsid} has archived URL of s3://${archivalMetaIncoming.externalArchiveDevice}/${archivalMetaIncoming.externalArchivePath} but expected s3://${elem.s3Bucket}/${elem.s3Path}")
            failStage(new RuntimeException("Incorrect archived URL"))
          }

          val writeFut = if(shouldWrite){
            val uriPath = s"/API/item/${elem.nearlineItem.membership.get.itemId}/metadata"

            val updatedArchiveReport = archivalMetaIncoming.externalArchiveReport + s"\nFixed by remove-archive-nearline at ${ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)}\n"
            val updatedMetadataToWrite = ArchivalMetadata(
              ZonedDateTime.now(),
              elem.s3Bucket,
              ArchivalMetadata.AR_NONE,
              updatedArchiveReport,
              ArchivalMetadata.AS_ARCHIVED,
              elem.s3Path,
              externalArchiveDeleteShape = true,
              None
            )

            val updateXmlDoc = updatedMetadataToWrite.makeXml.toString()
            logger.debug(s"XML content to write: $updateXmlDoc")
            logger.info(s"Writing updated metadata to $uriPath...")
            val headersMap = Map[String,String](
              "Content-Type"->"application/xml",
              "Content-Length"->updateXmlDoc.length.toString
            )
            vsComm.request(VSCommunicator.OperationType.PUT, uriPath, Some(updateXmlDoc),headersMap).map({
              case Left(err)=>
                logger.error(s"Vidispine returned an error updating metadata: ${err.toString}")
                throw new RuntimeException("Vidispine error, consult logs for details")
              case Right(_)=>
                logger.info(s"Item updated")
                Some(updatedMetadataToWrite)
            })
          } else {
            Future(elem.vsMeta)
          }

          writeFut.onComplete({
            case Failure(err)=>
              logger.error(s"Could not update metadata: ", err)
              failedCb.invoke(err)
            case Success(metaUpdate)=>
              successCb.invoke(elem.copy(vsMeta = metaUpdate))
          })
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
