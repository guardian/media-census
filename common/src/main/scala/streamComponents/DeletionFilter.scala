package streamComponents

import java.sql.Connection

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.sksamuel.elastic4s.http.ElasticClient
import config.DatabaseConfiguration
import helpers.JdbcConnectionManager
import models.{AssetSweeperFile, MediaCensusEntry, MediaCensusIndexer}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class DeletionFilter(config:DatabaseConfiguration, esClient:ElasticClient) extends GraphStage[FlowShape[MediaCensusEntry,MediaCensusEntry]] {
  private final val in:Inlet[MediaCensusEntry] = Inlet.create("DeletionFilter.in")
  private final val out:Outlet[MediaCensusEntry] = Outlet.create("DeletionFilter.out")

  override def shape: FlowShape[MediaCensusEntry, MediaCensusEntry] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private var connection:Connection = _

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val stmt = connection.createStatement()
        val stmtSource = s"SELECT id FROM deleted_files WHERE filepath='${elem.originalSource.filepath}' and filename='${elem.originalSource.filename}'"
        logger.debug(stmtSource)
        val resultSet = stmt.executeQuery(stmtSource)

        if(resultSet.next()){
          //we have rows
          logger.debug(s"Found row for ${elem.originalSource.filepath}/${elem.originalSource.filename}")
          push(out, elem)
          stmt.close()
        } else {
          logger.debug(s"${elem.originalSource.filepath}/${elem.originalSource.filename} is not in deleted items table")
          stmt.close()
          pull(in)
        }
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
  }

}
