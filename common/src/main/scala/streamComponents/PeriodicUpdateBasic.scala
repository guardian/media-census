package streamComponents

import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.sksamuel.elastic4s.http.ElasticClient
import models.{JobHistory, JobHistoryDAO, MediaCensusIndexer}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * this class is an Akka flow that periodically updates the given job record with the stats running through it
  */
class PeriodicUpdateBasic[T](initialJobRecord:JobHistory, updateEvery:Int=100, includeStats:Boolean=false)(implicit esClient:ElasticClient, jobHistoryDAO: JobHistoryDAO)
  extends GraphStage[FlowShape[T, T]] {

  private final val in:Inlet[T] = Inlet.create("PeriodicUpdate.in")
  private final val out:Outlet[T] = Outlet.create("PeriodicUpdate.out")

  override def shape: FlowShape[T, T] = FlowShape.of(in, out)

  /**
    * this method is called when we are about to do an update, and allows a subclass to adjust the JobHistory
    * record before writing to the database
    * @param logger
    * @param itemsProcessed
    * @param currentRecord
    * @return
    */
  def readyToUpdate(logger: Logger, itemsProcessed: Long, currentRecord: JobHistory):Future[Either[Seq[String], JobHistory]] = Future(Right(currentRecord))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    var c=0
    var itemsProcessed:Long = initialJobRecord.itemsCounted

    /**
      * write the given record to the index, then invoke either failed or completed AsyncCallback to continue processing
      * @param toWrite [[JobHistory]] object to write
      * @param failedCb failure callback
      * @param completedCb completion callback
      * @return a Future, without anything or interest.
      */
    def writeNewRecord(toWrite:JobHistory, elem:T, failedCb:AsyncCallback[Throwable], completedCb:AsyncCallback[T]) = {
      jobHistoryDAO.put(toWrite).map({
        case Left(err) =>
          logger.error(s"Could not write update index record: $err")
          failedCb.invoke(new RuntimeException(s"Could not write updated index record: $err"))
          Left(err.toString)
        case Right(newVersion) =>
          logger.info(s"Update job $toWrite, new version is $newVersion")
          c = 0
          completedCb.invoke(elem)
          Right(newVersion)
      })
    }

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val completedCb = getAsyncCallback[T](elem=>push(out,elem))
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
              if(includeStats) {
                logger.info(s"Updating current running stats...")
                readyToUpdate(logger, itemsProcessed, currentRecord).map({
                  case Left(errorList) =>
                    Future(Left(errorList))
                  case Right(updatedRecord) =>
                    val toWrite = updatedRecord.copy(itemsCounted = itemsProcessed)
                    writeNewRecord(toWrite, elem, failedCb, completedCb)
                }).recover({
                  case err: Throwable =>
                    logger.error(s"calculate stats crashed: ", err)
                    failedCb.invoke(err)
                }) //indexer.calculateStats.flatMap
              } else {
                logger.info(s"No updating stats, only updating count")
                val toWrite = currentRecord.copy(itemsCounted = itemsProcessed)
                writeNewRecord(toWrite, elem, failedCb, completedCb)
              }
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
