package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.sksamuel.elastic4s.http.ElasticClient
import models.{JobHistory, JobHistoryDAO, MediaCensusEntry, MediaCensusIndexer}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * this class is an Akka flow that periodically updates the given job record with the stats running through it
  */
class PeriodicUpdate(initialJobRecord:JobHistory, updateEvery:Int=100)(implicit esClient:ElasticClient, jobHistoryDAO: JobHistoryDAO, indexer:MediaCensusIndexer)
  extends GraphStage[FlowShape[MediaCensusEntry, MediaCensusEntry]] {

  private final val in:Inlet[MediaCensusEntry] = Inlet.create("PeriodicUpdate.in")
  private final val out:Outlet[MediaCensusEntry] = Outlet.create("PeriodicUpdate.out")

  override def shape: FlowShape[MediaCensusEntry, MediaCensusEntry] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    var c=0
    var itemsProcessed:Long = 0

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val completedCb = getAsyncCallback[MediaCensusEntry](elem=>push(out,elem))
        val failedCb = getAsyncCallback[Throwable](err=>failStage(err))

        itemsProcessed+=1
        c+=1
        if(c>updateEvery){
          jobHistoryDAO.jobForUuid(initialJobRecord.jobId).flatMap({
            case Left(elasticError)=>Future(Left(Seq(elasticError.toString)))
            case Right(None)=>
              failedCb.invoke(new RuntimeException(s"Could not find any job to update in the index with the ID ${initialJobRecord.jobId}"))
              Future(Left(Seq("Could not find any job")))
            case Right(Some(currentRecord))=>
              logger.info(s"Updating current running stats...")
              indexer.calculateStats(esClient, currentRecord).flatMap({
                case Left(errorList)=>
                  Future(Left(errorList))
                case Right(updatedRecord)=>
                  val toWrite = updatedRecord.copy(itemsCounted = itemsProcessed)
                  jobHistoryDAO.put(toWrite).map({
                    case Left(err)=>
                      logger.error(s"Could not write update index record: $err")
                      failedCb.invoke(new RuntimeException(s"Could not write updated index record: $err"))
                      Left(err.toString)
                    case Right(newVersion)=>
                      logger.info(s"Update job $toWrite, new version is $newVersion")
                      c=0
                      completedCb.invoke(elem)
                  })
              }) //indexer.calculateStats.flatMap
          })  //jobForUuid.flatMap
        } else {
          push(out, elem)
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
