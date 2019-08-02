package streamComponents

import akka.actor.ActorSystem
import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import akka.stream.stage.{AbstractOutHandler, AsyncCallback, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSFile, VSLazyItem}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.{Failure, Success}
import scala.xml.XML
import scala.concurrent.duration._

/*
content – Comma-separated list of the types of content to retrieve, possible values are metadata, uri, shape, poster, thumbnail, access, merged-access, external.
 */
/**
  *
  * @param searchDoc
  * @param includeShape
  * @param pageSize
  * @param comm
  * @param actorSystem
  * @param mat
  */
class VSItemSearchSource(metadataFields:Seq[String], searchDoc:String, includeShape:Boolean, pageSize:Int=100, retryDelay:FiniteDuration=30.seconds)
                        (implicit val comm:VSCommunicator, actorSystem: ActorSystem, mat:Materializer)
  extends GraphStage[SourceShape[VSLazyItem]] {

  private val out:Outlet[VSLazyItem] = Outlet.create("VSItemSearchSource.out")

  override def shape: SourceShape[VSLazyItem] = SourceShape.of(out)

  def getNextPage(startAt:Int) = {
    val uri = s"/API/item;first=$startAt;number=$pageSize"

    val contentParam = Seq(
      if(includeShape) Some("shape") else None,
      if(metadataFields.nonEmpty) Some("metadata") else None,
    ).collect({case Some(elem)=>elem}).mkString(",")

    val fieldsParam = metadataFields.mkString(",")

    comm.request(VSCommunicator.OperationType.PUT, uri, Some(searchDoc), Map("Accept"->"application/xml"), Map("content"->contentParam,"field"->fieldsParam)).map(_.map(xmlString=>{
      val parsedData = XML.loadString(xmlString)
      (parsedData \ "item").map(VSLazyItem.fromXmlSearchStanza)
    }))
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    private var queue:Seq[VSLazyItem] = Seq()
    private var currentItem = 1 //VS starts enumerating from 1 not 0

    /**
      * either services the request from the queue or calls out to VS for more data, refills the queue and processes that.
      * recurses after a 30s delay if an error is returned from VS.
      * @param failedCb
      * @param newItemCb
      * @param completedCb
      */

    def processPull(failedCb:AsyncCallback[Throwable], newItemCb:AsyncCallback[VSLazyItem], completedCb:AsyncCallback[Unit]):Unit = {
      if (queue.nonEmpty) {
        logger.debug(s"Serving next item from queue")
        push(out, queue.head)
        queue = queue.tail
      } else {
        getNextPage(currentItem).onComplete({
          case Failure(err) =>
            logger.error(s"getNextPage crashed", err)
            failedCb.invoke(err)
          case Success(Left(httpError)) =>
            logger.error(s"VS returned an http error ${httpError.errorCode}: ${httpError.message}, retrying in 30 seconds")
            actorSystem.scheduler.scheduleOnce(retryDelay, () =>processPull(failedCb, newItemCb, completedCb))
          //failedCb.invoke(new RuntimeException(httpError.message))
          case Success(Right(moreItems)) =>
            logger.info(s"Got ${moreItems.length} more items from server, processed $currentItem items")
            queue ++= moreItems
            currentItem += moreItems.length
            if (queue.isEmpty) {
              completedCb.invoke(())
            } else {
              newItemCb.invoke(queue.head)
              queue = queue.tail
            }
        })
      }
    }

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        val failedCb = createAsyncCallback[Throwable](err=>failStage(err))
        val newItemCb = createAsyncCallback[VSLazyItem](item=>push(out, item))
        val completedCb = createAsyncCallback[Unit](_=>complete(out))

        processPull(failedCb, newItemCb, completedCb)
      }
    })
  }

}
