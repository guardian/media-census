package streamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, AsyncCallback, GraphStage, GraphStageLogic}
import com.sksamuel.elastic4s.http.ElasticClient
import models.{JobHistory, JobHistoryDAO, MediaCensusEntry, MediaCensusIndexer}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * this class is an Akka flow that periodically updates the given job record with the stats running through it
  */
class PeriodicUpdate[T](initialJobRecord:JobHistory, updateEvery:Int=100, includeStats:Boolean=true)(implicit esClient:ElasticClient, jobHistoryDAO: JobHistoryDAO, indexer:MediaCensusIndexer)
  extends PeriodicUpdateBasic[T](initialJobRecord, updateEvery, includeStats) {

  override def readyToUpdate(logger: Logger, itemsProcessed: Long, currentRecord: JobHistory) = {
    logger.info(s"Updating current running stats...")
    indexer.calculateStats(esClient, currentRecord).map({
      case Left(errorList) =>
        Left(errorList)
      case Right(updatedRecord) =>
        Right(updatedRecord.copy(itemsCounted = itemsProcessed))
    })
  }
}