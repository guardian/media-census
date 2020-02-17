import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet, SinkShape}
import akka.stream.scaladsl.{GraphDSL, Sink}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import vidispine.VSCommunicator.OperationType
import vidispine.{VSCommunicator, VSFile}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * asks Vidispine to delete each VSFile instance that comes through. A failure will fail the stage (terminating the stream
  * unless other error handling is present)
  * @param comm implicitly provided VSCommunicator to talk to Vidispine
  * @param mat implicitly provided ActorMaterializer
  * @param ec implicitly provided ExecutionContext for async operations
  */
class VSDeleteFile(reallyDelete:Boolean)(implicit comm:VSCommunicator, mat:Materializer, ec:ExecutionContext) extends GraphStage[FlowShape[VSFile,VSFile]] {
  private final val in:Inlet[VSFile] = Inlet.create("VSDeleteFile.in")
  private final val out:Outlet[VSFile] = Outlet.create("VSDeleteFile.out")

  override def shape: FlowShape[VSFile, VSFile] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    private val successCb = createAsyncCallback[VSFile](f=>push(out,f))
    private val failedCb = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val url = s"/API/file/${elem.vsid}"
        val requestFuture = if(reallyDelete) {
          logger.info(s"Sending DELETE request to $url...")
          comm.request(OperationType.DELETE, url, None, Map(), Map())
        } else {
          logger.info(s"I would send DELETE request to $url if rellyDelete was on. Ignore 'delete completed' message")
          Future(Right(""))
        }

        requestFuture.onComplete({
          case Success(Right(_))=>
            logger.info(s"Delete completed successfully")
            successCb.invoke(elem)
          case Success(Left(httpError))=>
            if(httpError.errorCode==404){
              logger.warn(s"Tried to delete file ${elem.vsid} (${elem.path}) which was already missing!")
              successCb.invoke(elem)
            } else {
              logger.error(s"Could not delete file: Vidispine returned an error ${httpError.errorCode}: ${httpError.message}")
              failedCb.invoke(new RuntimeException(httpError.message))
            }
          case Failure(err)=>
            logger.error(s"Delete operation request crashed: ", err)
            failedCb.invoke(err)
        })
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}

object VSDeleteFile {
  /**
    * provdes VSDeleteFile as a Sink shape rather than a Flow shape
    * @param comm implicitly provided [[VSCommunicator]] object
    * @return a Sink for [[VSFile]] instances
    */
  def asSink(reallyDelete:Boolean)(implicit comm:VSCommunicator, mat:Materializer, ec:ExecutionContext):Sink[VSFile, NotUsed] = {
    val partialGraph = GraphDSL.create() { implicit builder=>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      val deleteFileFlow = builder.add(new VSDeleteFile(reallyDelete))
      val fakeSink = Sink.ignore

      deleteFileFlow ~> fakeSink
      SinkShape(deleteFileFlow.in)
    }
    Sink.fromGraph(partialGraph)
  }
}