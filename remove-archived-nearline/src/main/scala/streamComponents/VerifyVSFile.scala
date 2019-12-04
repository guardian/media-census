package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.ArchivedItemRecord
import org.slf4j.LoggerFactory
import vidispine.VSCommunicator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * checks whether the named file actually exists in VS.  Pushes to "yes" if it does and "no" if it doesn't
  */
class VerifyVSFile (implicit vsComm:VSCommunicator, mat:Materializer) extends GraphStage[UniformFanOutShape[ArchivedItemRecord, ArchivedItemRecord]] {
  private final val in:Inlet[ArchivedItemRecord] = Inlet.create("VerifyVSFile.in")
  private final val yes:Outlet[ArchivedItemRecord] = Outlet.create("VerifyVSFile.yes")
  private final val no:Outlet[ArchivedItemRecord] = Outlet.create("VerifyVSFile.no")

  override def shape = new UniformFanOutShape[ArchivedItemRecord,ArchivedItemRecord](in,Array(yes,no))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger:org.slf4j.Logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      val yesCb = createAsyncCallback[ArchivedItemRecord](rec=>push(yes,rec))
      val noCb = createAsyncCallback[ArchivedItemRecord](rec=>push(no,rec))
      val failedCb = createAsyncCallback[Throwable](err=>failStage(err))

      override def onPush(): Unit = {
        val elem = grab(in)

        val uriPath = s"/API/file/${elem.nearlineItem.vsid}"
        vsComm.request(VSCommunicator.OperationType.GET,uriPath,None,Map(),Map()).onComplete({
          case Success(Right(content))=>
            logger.debug(s"VS file ${elem.nearlineItem.vsid} does exist")
            yesCb.invoke(elem)
          case Success(Left(httpError))=>
            httpError.errorCode match {
              case 404=>
                logger.debug(s"VS file ${elem.nearlineItem.vsid} does not exist")
                noCb.invoke(elem)
              case _=>
                logger.error(s"Unexpected response from Vidispine: ${httpError.errorCode} ${httpError.message}")
                failedCb.invoke(new RuntimeException("Unexpected response from Vidispine"))
            }
          case Failure(err)=>
            logger.error(s"VS lookup crashed: ", err)
            failedCb.invoke(err)
        })
      }
    })

    val genericHandler = new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    }

    setHandler(yes, genericHandler)
    setHandler(no, genericHandler)
  }
}
