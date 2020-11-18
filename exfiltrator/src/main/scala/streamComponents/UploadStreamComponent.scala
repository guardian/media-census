package streamComponents

import akka.actor.ActorSystem
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.gu.vidispineakka.vidispine.VSFile
import org.slf4j.LoggerFactory
import utils.Uploader

import scala.concurrent.ExecutionContext

class UploadStreamComponent(uploader:Uploader)(implicit actorSystem:ActorSystem) extends GraphStage[FlowShape[VSFile,VSFile]] {
  private final val logger = LoggerFactory.getLogger(getClass)
  private final val in:Inlet[VSFile] = Inlet.create("UploadStreamComponent.in")
  private final val out:Outlet[VSFile] = Outlet.create("UploadStreamComponent.out")

  private implicit val ec:ExecutionContext = actorSystem.dispatcher

  override def shape: FlowShape[VSFile, VSFile] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val successCb = createAsyncCallback[VSFile](vsFile=>push(out, vsFile))
    val ignoreCb  = createAsyncCallback[Unit](_=>pull(in))
    val errorCb = createAsyncCallback[Throwable](err=>{
      logger.error(s"Could not handle upload: ", err)
      failStage(err)
    })

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        uploader.handleUnarchivedFile(elem)
          .map(uploadResults=>{
            if(uploadResults.isEmpty) {
              logger.info(s"File ${elem.vsid} (${elem.uri}) was ignored")
              ignoreCb.invoke( () )
            } else {
              logger.info(s"File ${elem.vsid} (${elem.uri}) uploaded ${uploadResults.length} files")
              successCb.invoke(elem)
            }
          })
          .recover({
            case err:Throwable=>
              errorCb.invoke(err)
          })
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
