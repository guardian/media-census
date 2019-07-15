package mfstreamcomponents

import akka.stream.{Attributes, Inlet, Materializer, SinkShape}
import akka.stream.stage.{AbstractInHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSEntry}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * sends a "file record delete" request to Vidispine for the given input
  * @param reallyDelete if this is false, then just indicate deletion rahter than doing it
  * @param failFast if false, then don't fail the stage if the VS call errors.
  * @param comm implicitly provided VSCommunicator
  * @param mat implicitly provided akka materializer
  */
class DeleteFileSink(reallyDelete:Boolean, failFast:Boolean)(implicit comm:VSCommunicator, mat:Materializer) extends GraphStage[SinkShape[VSEntry]] {
  private val in:Inlet[VSEntry] = Inlet.create("DeleteFileSink.in")

  override def shape: SinkShape[VSEntry] = SinkShape.of(in)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val completedCb = createAsyncCallback[Unit](_=>pull(in))
        val failedCb = createAsyncCallback[Throwable](err=>failStage(err))

        elem.vsFile match {
          case Some(vsFile) =>
            if(reallyDelete){
              logger.info(s"Requesting deletion of VS file record ${vsFile.vsid}")
              comm.requestDelete("/API/storage/file/" + vsFile.vsid, Map()).onComplete({
                case Failure(exception)=>
                  logger.error(s"requestDelete crashed: ", exception)
                  failedCb.invoke(exception)

                case Success(Left(err))=>
                  logger.error(s"Could not request deletion from VS: $err")
                  if(failFast){
                    failedCb.invoke(new RuntimeException(err.toString))
                  } else {
                    completedCb.invoke()
                  }

                case Success(Right(_))=>
                  logger.info(s"Deletion request successful")
                  completedCb.invoke()
              })
            } else {
              logger.info(s"I would delete file ${vsFile.vsid}")
              pull(in)
            }
        }
      }
    })
  }
}
