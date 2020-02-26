package streamcomponents

import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import akka.stream.stage.{AbstractOutHandler, GraphStage, GraphStageLogic}
import models.PlutoProject
import org.slf4j.LoggerFactory
import vidispine.VSCommunicator

import scala.util.{Failure, Success}

class PlutoProjectSource(recordsPerPage:Int=5)(implicit comm:VSCommunicator,mat:Materializer) extends GraphStage[SourceShape[PlutoProject]] {
  private final val out: Outlet[PlutoProject] = Outlet.create("PlutoProjectSource")

  override def shape: SourceShape[PlutoProject] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger:org.slf4j.Logger = LoggerFactory.getLogger(getClass)
    private var buffer:Seq[PlutoProject] = Seq()
    private var currentRecord:Int = 0

    setHandler(out, new AbstractOutHandler {
      val onOutputHandler = createAsyncCallback[PlutoProject](result => push(out, result))
      val onCompleteHandler = createAsyncCallback[Unit](_ => completeStage())
      val onErrorHandler = createAsyncCallback[Throwable](err => failStage(err))

      override def onDownstreamFinish(): Unit = {
        println("downstream finished, terminating")
      }

      override def onPull(): Unit = {
        if(buffer.isEmpty) {
          val responseFuture = comm.request(VSCommunicator.OperationType.GET,
            "/project/api/extsearch/",
            None,
            Map(),
            Map("status"->"In Production","limit"->"100000")
          )

          responseFuture.onComplete({
            case Failure(err) =>
              logger.error(s"Pluto request crashed: ", err)
              onErrorHandler.invoke(err)
            case Success(Left(vserr)) =>
              logger.error(s"Pluto returned a ${vserr.errorCode} error: ${vserr.message}")
              onErrorHandler.invoke(new RuntimeException(s"Pluto returned a ${vserr.errorCode}"))
            case Success(Right(rawJson)) =>
              io.circe.parser.parse(rawJson).flatMap(_.as[List[PlutoProject]]) match {
                case Left(jsonErr)=>
                  logger.error(s"Could not understand server response: ${jsonErr.toString}")
                  onErrorHandler.invoke(new RuntimeException("Could not understand server response"))
                case Right(projectList)=>
                  this.synchronized {
                    buffer = buffer ++ projectList
                  }
                  if(buffer.nonEmpty) {
                    val head = buffer.head
                    this.synchronized {
                      buffer = buffer.tail
                    }
                    logger.debug(s"Got ${buffer.length} more items")
                    onOutputHandler.invoke(head)
                  } else {
                    logger.info("Processed all projects")
                    onCompleteHandler.invoke( () )
                  }
              }
          })
        } else {
          val head = buffer.head
          this.synchronized {
            buffer = buffer.tail
          }
          logger.debug(s"Pushing next project, got ${buffer.length} remaining")
          push(out, head)
        }

      }
    })

  }
}