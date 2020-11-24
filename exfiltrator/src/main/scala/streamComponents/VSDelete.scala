package streamComponents

import akka.actor.ActorSystem
import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import com.gu.vidispineakka.vidispine.VSCommunicator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * delete the VS item and file mentioned
 * @param reallyDelete if false, output messages but don't actually delete
 * @param vsComm implicitly provided VSCommunicator
 * @param mat implicitly provided Materializer
 * @param actorSystem implicitly provided ActorSystem
 */
class VSDelete(reallyDelete:Boolean)(implicit vsComm:VSCommunicator, mat:Materializer, actorSystem:ActorSystem) extends GraphStage[FlowShape[ExfiltratorStreamElement, ExfiltratorStreamElement]] {
  private final val in:Inlet[ExfiltratorStreamElement] = Inlet.create("VSDelete.in")
  private final val out:Outlet[ExfiltratorStreamElement] = Outlet.create("VSDelete.out")

  override def shape: FlowShape[ExfiltratorStreamElement, ExfiltratorStreamElement] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    val completionCb = createAsyncCallback[ExfiltratorStreamElement](el=>push(out, el))
    val errorCb = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val deletionFuture = elem.maybeItem match {
          case Some(item)=>
            val targetUri = s"/API/item/${item.itemId}"
            if(reallyDelete) {
              logger.info(s"Deleting item at $targetUri...")
              vsComm.request(VSCommunicator.OperationType.DELETE, targetUri, None, Map())
            } else {
              logger.info(s"I would send DELETE to $targetUri")
              Future(Right("ignored"))
            }
          case None=>
            logger.info(s"No item is present, deleting file at ${elem.file.vsid}")
            val targetUri = s"/API/storage/file/${elem.file.vsid}"
            if(reallyDelete) {
              logger.info(s"Deleting file at $targetUri")
              vsComm.request(VSCommunicator.OperationType.DELETE, targetUri, None, Map())
            } else {
              logger.info(s"I would send DELETE to $targetUri")
              Future(Right("ignored"))
            }
        }

        deletionFuture.onComplete({
          case Failure(err)=>
            logger.error(s"Could not process deletion request for $elem: ", err)
            errorCb.invoke(err)
          case Success(Left(httpErr))=>
            logger.error(s"Could not process deletion request for $elem: ${httpErr.errorCode} ${httpErr.message}")
            if(httpErr.errorCode==404) {
              if(elem.maybeItem.isDefined) {
                logger.warn(s"Item ${elem.maybeItem.get} did not exist, can't delete a non-existent item")
              } else {
                logger.warn(s"File ${elem.file} did not exist, can't delete a non-existent file")
              }
              completionCb.invoke(elem)
            } else {
              errorCb.invoke(new RuntimeException(s"Deletion request failed: ${httpErr.errorCode}"))
            }
          case Success(Right(xmlContent))=>
            logger.debug(s"Vidispine returned $xmlContent")
            completionCb.invoke(elem)
        })
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
