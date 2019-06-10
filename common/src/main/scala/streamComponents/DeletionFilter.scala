package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.sksamuel.elastic4s.http.ElasticClient
import models.{AssetSweeperFile, MediaCensusIndexer}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class DeletionFilter(indexer:MediaCensusIndexer, esClient:ElasticClient) extends GraphStage[FlowShape[AssetSweeperFile,AssetSweeperFile]] {
  private final val in:Inlet[AssetSweeperFile] = Inlet.create("DeletionFilter.in")
  private final val out:Outlet[AssetSweeperFile] = Outlet.create("DeletionFilter.out")

  override def shape: FlowShape[AssetSweeperFile, AssetSweeperFile] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val completedCb = getAsyncCallback[AssetSweeperFile](finalEntry=>push(out, finalEntry))
        val failedCb = getAsyncCallback[Throwable](err=>failStage(err))
        val ignoreCb = getAsyncCallback[AssetSweeperFile](_=>pull(in))

        indexer.doesEntryExist(esClient, elem.id.toString).map({
          case Left(err)=>
            logger.error(s"Could not check for existence of census entry: $err")
            failedCb.invoke(new RuntimeException(err.toString))
          case Right(true)=>
            logger.debug(s"Entry for ${elem.id} exists in census")
            completedCb.invoke(elem)
          case Right(false)=>
            logger.debug(s"Entry for ${elem.id} does not exist in census")
            ignoreCb.invoke(elem)
        })
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
