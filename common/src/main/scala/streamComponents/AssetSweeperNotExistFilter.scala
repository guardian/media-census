package streamComponents

import java.sql.Connection

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import config.DatabaseConfiguration
import helpers.JdbcConnectionManager
import models.MediaCensusEntry
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

/**
  * filters out records that DO exist in the asset sweeper files table
  */
class AssetSweeperNotExistFilter(config:DatabaseConfiguration) extends GraphStage[FlowShape[MediaCensusEntry, MediaCensusEntry]] {
  private final val in:Inlet[MediaCensusEntry] = Inlet("AssetSweeperNotExistFilter.in")
  private final val out:Outlet[MediaCensusEntry] = Outlet("AssetSweeperNotExistFilter.out")

  override def shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private var connection:Connection = _
    private lazy val checkStatement = connection.prepareStatement(s"SELECT id FROM files WHERE filepath=? and filename=?")

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        checkStatement.setString(1, elem.originalSource.filepath)
        checkStatement.setString(2, elem.originalSource.filename)
        val resultSet = checkStatement.executeQuery()

        if(resultSet.next()){
          //we have rows
          logger.debug(s"Found row for ${elem.originalSource.filepath}/${elem.originalSource.filename} in files table")
          pull(in)
        } else {
          logger.debug(s"${elem.originalSource.filepath}/${elem.originalSource.filename} is not in the files table")
          push(out, elem)
        }
        resultSet.close()
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
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

    override def postStop(): Unit = {
      checkStatement.close()
    }
  }
}
