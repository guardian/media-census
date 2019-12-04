package streamComponents

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.sun.org.apache.xpath.internal.axes.NodeSequence
import models.ArchivedItemRecord
import org.slf4j.LoggerFactory
import vidispine.{ArchivalMetadata, VSCommunicator}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.xml.NodeSeq

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
      (!meta.get.externalArchiveRequest.contains(ArchivalMetadata.AR_NONE) ||
      !meta.get.externalArchiveStatus.contains(ArchivalMetadata.AS_ARCHIVED),
        meta.get.externalArchiveDevice.contains(s3Bucket) ||
        meta.get.externalArchivePath.contains(s3Path)
        )
    }
  }

  def makeXmlDoc(content:NodeSeq) =
    <MetadataDocument xmlns="http://xml.vidispine.com/schema/vidispine">
      {content}
    </MetadataDocument>

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

          logger.debug(s"Got existing archival metadata ${elem.vsMeta.map(_.makeXml.toString)}")
          val (shouldWrite,correctPath) = needsFix(elem.vsMeta, elem.s3Bucket, elem.s3Path)
          if(!correctPath){
            logger.error(s"Vidispine item for file ${elem.nearlineItem.vsid} has archived URL of s3://${archivalMetaIncoming.externalArchiveDevice}/${archivalMetaIncoming.externalArchivePath} but expected s3://${elem.s3Bucket}/${elem.s3Path}")
            failStage(new RuntimeException("Incorrect archived URL"))
          }

          val writeFut = if(shouldWrite){
            val uriPath = s"/API/item/${elem.nearlineItem.membership.get.itemId}/metadata"

            val updatedArchiveReport = archivalMetaIncoming.externalArchiveReport + s"\nFixed by remove-archive-nearline at ${ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)}\n"
            val updatedMetadataToWrite = ArchivalMetadata(
              Some(ZonedDateTime.now()),
              Some(elem.s3Bucket),
              Some(ArchivalMetadata.AR_NONE),
              Some(updatedArchiveReport),
              Some(ArchivalMetadata.AS_ARCHIVED),
              Some(elem.s3Path),
              externalArchiveDeleteShape = Some(true),
              None
            )

            val updateXmlDoc = makeXmlDoc(updatedMetadataToWrite.makeXml).toString()
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
            Future(elem.vsMeta)
          } else {
            logger.info(s"Item ${elem.nearlineItem.membership.get.itemId} did not need metadata update")
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
