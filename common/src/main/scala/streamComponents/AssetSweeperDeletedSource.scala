package streamComponents

import java.sql.Connection

import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{AbstractOutHandler, GraphStage, GraphStageLogic}
import config.DatabaseConfiguration
import helpers.JdbcConnectionManager
import models.{AssetSweeperFile, MediaCensusEntry}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

class AssetSweeperDeletedSource(config:DatabaseConfiguration, startAt:Option[Long]=None, totalLimit:Option[Long]=None) extends GraphStage[SourceShape[AssetSweeperFile]]{
  private final val out:Outlet[AssetSweeperFile] = Outlet.create("AssetSweeperFilesSource.out")

  override def shape: SourceShape[AssetSweeperFile] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    var connection:Connection = _
    var lastProcessed:Long = startAt match {
      case None=>0
      case Some(startingIdx)=>startingIdx
    }
    val pageSize:Int = 20
    var processingQueue:Seq[AssetSweeperFile] = Seq()

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        if(totalLimit.isDefined && lastProcessed>=totalLimit.get){
          logger.info(s"Processed up to limit of ${totalLimit.get}, stopping")
          complete(out)
          return
        }

        if(processingQueue.isEmpty){
          val statement = connection.createStatement()
          val stmtSource = s"SELECT * FROM deleted_files ORDER BY id asc OFFSET $lastProcessed LIMIT $pageSize"
          logger.debug(stmtSource)
          val resultSet = statement.executeQuery(stmtSource)

          while(resultSet.next()){
            AssetSweeperFile.fromDeletionTableResultSet(resultSet) match {
              case Failure(err)=>
                logger.error("Could not marshal result set into object; this entry will be dropped: ", err)
              case Success(assetSweeperFile)=>
                processingQueue ++= Seq(assetSweeperFile)
            }
          }
        } //processingQueue.isEmpty
        processingQueue.headOption match {
          case Some(nextElem)=>
            processingQueue = processingQueue.tail
            lastProcessed+=1
            logger.debug(s"Outputting $nextElem")
            push(out, nextElem)
          case None=>
            logger.info(s"Rendered all items")
            complete(out)
        }
      }

    })

    override def preStart(): Unit = {
      JdbcConnectionManager.getConnectionForSection(config) match {
        case Failure(err)=>
          logger.error(s"Could not get connection for asset sweeper db: ", err)
          failStage(err)
        case Success(conn)=>
          logger.info("Established connection to asset sweeper db")
          connection = conn
      }
    }
  }

}
