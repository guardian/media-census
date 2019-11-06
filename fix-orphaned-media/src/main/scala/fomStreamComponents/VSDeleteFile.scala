package fomStreamComponents

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream._
import models.HttpError
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSFile}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class VSDeleteFile(comm:VSCommunicator)(implicit mat:Materializer) extends GraphStage[FlowShape[VSFile,VSFile]] {
  private final val in:Inlet[VSFile] = Inlet.create("VSDeleteFile.in")
  private final val out:Outlet[VSFile] = Outlet.create("VSDeleteFile.out")
  private val outerLogger = LoggerFactory.getLogger(getClass)

  override def shape: FlowShape[VSFile, VSFile] = FlowShape.of(in,out)

  def attemptDeleteWithRetry(storageId:String, fileId:String, attempt:Int=0):Future[Either[HttpError,String]] = {
    comm.request(VSCommunicator.OperationType.DELETE,s"/API/storage/$storageId/file/$fileId",None,Map()).flatMap({
      case Left(err)=>
        if(err.errorCode==503 || err.errorCode==504){
          outerLogger.error(s"Vidispine timed out on attempt $attempt, retrying in 2s")
          Thread.sleep(2000)
          attemptDeleteWithRetry(storageId,fileId,attempt+1)
        } else {
          Future(Left(err))
        }
      case success@Right(_)=>Future(success)
    })
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val completionCb = createAsyncCallback[VSFile](entry=>push(out,entry))
        val failureCb = createAsyncCallback[Throwable](err=>failStage(err))

        logger.info(s"Attempting to delete file ${elem.vsid} on storage ${elem.storage}")

        if(elem.storage!="KP-2" || elem.storage!="KP-3"){
          logger.error(s"Received file on storage ${elem.storage} which is not nearline, aborting")
          failStage(new RuntimeException("Safety error - received file not on nearline"))
        } else {
          attemptDeleteWithRetry(elem.storage, elem.vsid).onComplete({
            case Failure(err) =>
              logger.error("Delete operation crashed: ", err)
              failureCb.invoke(err)
            case Success(Left(err)) =>
              logger.error(s"Could not delete from Vidispine: $err")
              failureCb.invoke(new RuntimeException("Could not delete from Vidispine"))
            case Success(Right(_)) =>
              logger.info(s"Deleted incorrect file ${elem.vsid} from VS storage ${elem.storage}")
              completionCb.invoke(elem)
          })
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
