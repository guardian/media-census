package streamComponents

import java.sql.Connection

import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{AbstractOutHandler, GraphStage, GraphStageLogic}
import helpers.JdbcConnectionManager
import javax.inject.Inject
import models.AssetSweeperFile
import play.api.Logger

import scala.util.{Failure, Success}

/**
  * akka source to retrieve items from the given (existing) jdbc database and yield them to the stream
  * @param jdbcConnectionManager implicitly provided JdbcConnectionManager object
  */
class AssetSweeperFilesSource @Inject() (jdbcConnectionManager: JdbcConnectionManager) extends GraphStage[SourceShape[AssetSweeperFile]] {
  private final val out:Outlet[AssetSweeperFile] = Outlet.create("AssetSweeperFilesSource.out")

  override def shape: SourceShape[AssetSweeperFile] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = Logger(getClass)
    var connection:Connection = _
    var lastProcessed:Int = 0
    val pageSize:Int = 20
    var processingQueue:Seq[AssetSweeperFile] = Seq()

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        if(processingQueue.isEmpty){
          val statement = connection.createStatement()
          val stmtSource = s"SELECT * FROM files OFFSET $lastProcessed LIMIT $pageSize"
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
        } //processingQueue.isEmpty
        processingQueue.headOption match {
          case Some(nextElem)=>
            processingQueue = processingQueue.tail
            lastProcessed+=1
            push(out, nextElem)
          case None=>
            logger.info(s"Rendered all items")
            complete(out)
        }
      }

    })

    override def preStart(): Unit = {
      jdbcConnectionManager.getConnectionForSection("assetSweeper") match {
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
