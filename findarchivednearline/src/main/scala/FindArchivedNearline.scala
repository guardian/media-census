import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import config.VSConfig
import models.ItemShapeReport
import org.slf4j.LoggerFactory
import streamComponents.{ItemArchivedCheck, VSItemSearchSource}

import scala.concurrent.Await
import scala.concurrent.duration._
import com.softwaremill.sttp._
import fanStreamComponents.GenerateShapesReport
import vidispine.{FieldNames, VSCommunicator}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object FindArchivedNearline {
  val logger = LoggerFactory.getLogger(getClass)

  private implicit val actorSystem = ActorSystem("FixMissingFiles")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)

  lazy val vsConfig = VSConfig(
    uri"${sys.env("VIDISPINE_BASE_URL")}",
    sys.env("VIDISPINE_USER"),
    sys.env("VIDISPINE_PASSWORD")
  )

  lazy val knownOnlineStorages = sys.env.get("KNOWN_ONLINE_STORAGES") match {
    case None=>
      logger.error("You must specify KNOWN_ONLINE_STORAGES in the environment, as a comma-delimited list of storage IDs")
      terminate(1)
      Array()
    case Some(storages)=>storages.split("\\s*,\\s*")
  }

  lazy val knownNearlineStorages = sys.env.get("KNOWN_NEARLINE_STORAGES") match {
    case None=>
      logger.error("You must specify KNOWN_NEARLINE_STORAGES in the environment, as a comma-delimited list of storage IDs")
      terminate(1)
      Array()
    case Some(storages)=>storages.split("\\s*,\\s*")
  }

  lazy implicit val vsCommunicator = new VSCommunicator(vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass)

  val interestingFields = Seq(
    FieldNames.EXTERNAL_ARCHIVE_DEVICE,FieldNames.EXTERNAL_ARCHIVE_PATH,FieldNames.EXTERNAL_ARCHIVE_REQUEST,
    FieldNames.EXTERNAL_ARCHIVE_STATUS,FieldNames.EXTERNAL_ARCHIVE_STATUS,FieldNames.EXTERNAL_ARCHIVE_COMMITTED_AT,
    FieldNames.EXTERNAL_ARCHIVE_DELETE_SHAPE,FieldNames.EXTERNAL_ARCHIVE_LAST_RESTORE,FieldNames.EXTERNAL_ARCHIVE_REPORT
  )

  val searchDocString =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">
      | 	<field>
      |		<name>gnm_external_archive_external_archive_status</name>
      |		<value>Archived</value>
      |	</field>
      |</ItemSearchDocument>
    """.stripMargin

  def terminate(exitCode:Int) = {
    Await.ready(actorSystem.terminate(), 3 minutes)
    System.exit(exitCode)
  }

  def buildGraph() = {
    val sinkFactory = Sink.fold[Seq[ItemShapeReport],ItemShapeReport](Seq())((acc, item)=>acc++Seq(item))

    GraphDSL.create(sinkFactory){ implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new VSItemSearchSource(interestingFields, searchDocString,includeShape=true, pageSize=20).async)
      val verifier = builder.add(new ItemArchivedCheck)
      val reportgen = builder.add(new GenerateShapesReport(knownOnlineStorages,knownNearlineStorages))

      src ~> verifier ~> reportgen ~> sink
      ClosedShape
    }
  }

  def main(args: Array[String]): Unit = {
    RunnableGraph.fromGraph(buildGraph()).run().onComplete({
      case Failure(err)=>
        logger.error("Main stream failed: ",err)
        terminate(1)
      case Success(shapeReports)=>
        val noItemCount = shapeReports.count(_.noOriginalShape==true)
        val onlyNearlineCount = shapeReports.count(report=>report.nearlineShapeCount>0 && report.onlineShapeCount==0 && report.otherShapeCount==0)
        val onlyOnlineCount = shapeReports.count(report=>report.onlineShapeCount>0 && report.nearlineShapeCount==0 && report.otherShapeCount==0)
        val onlineAndNearlineCount = shapeReports.count(report=>report.onlineShapeCount>0 && report.nearlineShapeCount>0 && report.otherShapeCount==0)
        val otherShapesCount = shapeReports.count(report=>report.otherShapeCount>0)

        logger.info(s"Stats report:")
        logger.info(s"Out of a total of ${shapeReports.length} items scanned, $noItemCount were correct (no original shape present).")
        logger.info(s"$onlyNearlineCount had shapes on nearline only, $onlyOnlineCount had shapes on online only, $onlineAndNearlineCount had shapes on both online and nearline")
        logger.info(s"$otherShapesCount had shapes only on unrecognised storages")
        terminate(0)
    })
  }
}
