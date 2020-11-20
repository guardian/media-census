package streamComponents

import akka.actor.ActorSystem

import scala.util.{Failure, Success}
import akka.stream.{Attributes, Inlet, Materializer, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSFile}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class DoesFileExist(implicit vsComm:VSCommunicator, mat:Materializer, actorSystem:ActorSystem) extends GraphStage[UniformFanOutShape[VSFile, VSFile]]{
  private final val in:Inlet[VSFile] = Inlet.create("DoesFileExist.in")
  private final val yes:Outlet[VSFile] = Outlet.create("DoesFileExist.yes")
  private final val no:Outlet[VSFile] = Outlet.create("DoesFileExist.no")

  override def shape = UniformFanOutShape[VSFile, VSFile](in, yes, no)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    val yesCb = createAsyncCallback[VSFile](f=>push(yes, f))
    val noCb = createAsyncCallback[VSFile](f=>push(no, f))
    val errorCb = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        vsComm.request(VSCommunicator.OperationType.GET, s"/API/storage/file/${elem.vsid}", None, Map()).onComplete({
          case Failure(err)=>
            logger.error(s"VS lookup for file ${elem.vsid} failed: ", err)
            errorCb.invoke(err)
          case Success(Left(httpError))=>
            if(httpError.errorCode==404) {
              logger.info(s"File ${elem.vsid} does not exist any more, ignoring")
              noCb.invoke(elem)
            } else {
              logger.error(s"Vidispine error getting file ${elem.vsid}: ${httpError.errorCode} ${httpError.message}")
              errorCb.invoke(new RuntimeException(s"Vidispine http error ${httpError.errorCode}"))
            }
          case Success(Right(_))=>
            logger.debug(s"File ${elem.vsid} still exists")
            yesCb.invoke(elem)
        })
      }
    })

    val genericOutHandler = new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    }

    setHandler(yes, genericOutHandler)
    setHandler(no, genericOutHandler)
  }
}
