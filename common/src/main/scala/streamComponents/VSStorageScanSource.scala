package streamComponents

import akka.actor.ActorSystem
import akka.stream.stage.{AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import com.softwaremill.sttp.Uri
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSFile}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class VSStorageScanSource(storageId:Option[String], fileState:Option[String], vsBaseUri:Uri, user:String, pass:String, pageSize:Int=10, maxRetries:Int=5)(implicit val actorSystem: ActorSystem, mat:Materializer, ec:ExecutionContext) extends GraphStage[SourceShape[VSFile]]{
  private final val out:Outlet[VSFile] = Outlet("VSStorageScanSource.out")

  override def shape: SourceShape[VSFile] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    private val comm = new VSCommunicator(vsBaseUri, user, pass)
    private var listQueue:Seq[VSFile] = Seq()
    private var ctr=0

    def getNextPage(retryIdx:Int=0):Future[Either[String,Seq[VSFile]]] = {
      val baseUrl = storageId match {
        case Some(actualStorageId)=>s"/API/storage/$actualStorageId/file"
        case None=>"/API/storage/file"
      }

      val queryParams = Map("count"->"false")

      val queryParamsWithState = if(fileState.isDefined) queryParams ++ Map("state"->fileState.get) else queryParams

      comm.requestGet(s"$baseUrl;first=$ctr;number=$pageSize;sort=timestamp",Map("Accept"->"application/xml"),queryParams = queryParamsWithState).flatMap({
        case Left(err)=>
          logger.warn(s"Got HTTP error $err listing storage $storageId. Retrying...")
          Thread.sleep(5000)
          if(retryIdx<maxRetries) {
            getNextPage(retryIdx + 1)
          } else {
            Future(Left(err.toString))
          }
        case Right(result)=>
          Future(VSFile.seqFromXmlString(result) match {
            case Left(errSeq)=>Left(errSeq.map(_.toString).mkString(","))
            case Right(results)=>Right(results)
          })
      })
    }

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        val completedCb = getAsyncCallback[Option[VSFile]]({
          case Some(vsfile)=>
            logger.info(vsfile.toString)
            push(out, vsfile)
            listQueue = listQueue.tail
          case None=>
            complete(out)
        })

        val failedCb = getAsyncCallback[Throwable](err=>failStage(err))

        if(listQueue.isEmpty) {
          logger.info(s"Getting next page of results...")
          getNextPage().onComplete({
            case Failure(err) =>
              logger.error("Could not list files from storage: ", err)
              failedCb.invoke(err)
            case Success(Left(err)) =>
              logger.error(s"Could not list files from storage: $err")
              failedCb.invoke(new RuntimeException(err))
            case Success(Right(fileList)) =>
              logger.debug(s"Got ${fileList.length} more results")
              listQueue ++= fileList
              ctr+=fileList.length
              completedCb.invoke(listQueue.headOption)
          })
        } else {
          completedCb.invoke(listQueue.headOption)
        }
      }
    })

    override def preStart(): Unit = {

    }
  }
}
