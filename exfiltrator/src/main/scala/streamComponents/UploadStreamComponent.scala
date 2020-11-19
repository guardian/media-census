package streamComponents

import akka.actor.ActorSystem
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.gu.vidispineakka.vidispine.VSFile
import org.slf4j.LoggerFactory
import utils.Uploader

import scala.concurrent.ExecutionContext

class UploadStreamComponent(uploader:Uploader)(implicit actorSystem:ActorSystem) extends GraphStage[FlowShape[ExfiltratorStreamElement,ExfiltratorStreamElement]] {
  private final val logger = LoggerFactory.getLogger(getClass)
  private final val in:Inlet[ExfiltratorStreamElement] = Inlet.create("UploadStreamComponent.in")
  private final val out:Outlet[ExfiltratorStreamElement] = Outlet.create("UploadStreamComponent.out")

  private implicit val ec:ExecutionContext = actorSystem.dispatcher

  override def shape: FlowShape[ExfiltratorStreamElement, ExfiltratorStreamElement] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val successCb = createAsyncCallback[ExfiltratorStreamElement](vsFile=>push(out, vsFile))
    val ignoreCb  = createAsyncCallback[Unit](_=>pull(in))
    val errorCb = createAsyncCallback[Throwable](err=>{
      logger.error(s"Could not handle upload: ", err)
      failStage(err)
    })

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        uploader.handleUnarchivedFile(elem)
          .map(counts=>{
            val successCount = counts._1
            val totalCount = counts._2
            if(successCount==0) {
              logger.info(s"File ${elem.file.vsid} (${elem.file.path}) on item ${elem.maybeItem.map(_.itemId)} was ignored")
              ignoreCb.invoke( () )
            } else {
              logger.info(s"File ${elem.file.vsid} (${elem.file.path}) on item ${elem.maybeItem.map(_.itemId)} uploaded $successCount / $totalCount files")
              if(totalCount>0 && successCount==totalCount) {
                successCb.invoke(elem)
              } else {
                logger.warn("Some files failed to upload, not performing deletion")
                ignoreCb.invoke( () )
              }
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