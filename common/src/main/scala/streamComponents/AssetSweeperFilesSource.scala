package streamComponents

import java.sql.Connection

import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{AbstractOutHandler, GraphStage, GraphStageLogic}
import config.DatabaseConfiguration
import helpers.JdbcConnectionManager
import models.{AssetSweeperFile, MediaCensusEntry}
import org.slf4j.LoggerFactory
import scala.util.{Failure, Success}

/**
  * akka source to retrieve items from the given (existing) jdbc database and yield them to the stream
  */
class AssetSweeperFilesSource (config:DatabaseConfiguration, startAt:Option[Long]=None, totalLimit:Option[Long]=None) extends GraphStage[SourceShape[MediaCensusEntry]] {
  private final val out:Outlet[MediaCensusEntry] = Outlet.create("AssetSweeperFilesSource.out")

  override def shape: SourceShape[MediaCensusEntry] = SourceShape.of(out)

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
      override def onPull:Unit = try {
        onPullBody()
      } catch {
        case err:Throwable=>
          logger.error("Uncaught exception getting asset sweeper files", err)
          failStage(err)
      }

      def onPullBody(): Unit = {
        if(totalLimit.isDefined && lastProcessed>=totalLimit.get){
          logger.info(s"Processed up to limit of ${totalLimit.get}, stopping")
          complete(out)
          return
        }

        if(processingQueue.isEmpty){
          val statement = connection.createStatement()
          val stmtSource = s"SELECT * FROM files ORDER BY id asc OFFSET $lastProcessed LIMIT $pageSize"
          logger.debug(stmtSource)
          val resultSet = statement.executeQuery(stmtSource)

          while(resultSet.next()){
            AssetSweeperFile.fromResultSet(resultSet) match {
              case Failure(err)=>
                logger.error("Could not marshal result set into object: ", err)
                failStage(err)
              case Success(assetSweeperFile)=>
                processingQueue ++= Seq(assetSweeperFile)
            }
          }
          statement.close()
        } //processingQueue.isEmpty

        processingQueue.headOption match {
          case Some(nextElem)=>
            processingQueue = processingQueue.tail
            lastProcessed+=1
            push(out, MediaCensusEntry(nextElem,None,None,None,None,None,Seq(),0))
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
