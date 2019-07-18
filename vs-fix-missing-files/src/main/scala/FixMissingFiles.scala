import akka.actor.ActorSystem
import akka.stream.scaladsl.{GraphDSL, Merge, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import config.VSConfig
import org.slf4j.LoggerFactory
import com.softwaremill.sttp._
import mfmodels.{ItemReport, ItemStatus}
import mfstreamcomponents.{CheckArchivehunterSwitch, DeleteFileSink, FindAssociatedItem, VSFileHasItem, VSItemHasArchivePath}
import streamComponents.VSStorageScanSource
import vidispine.{VSCommunicator, VSEntry}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object FixMissingFiles {
  val logger = LoggerFactory.getLogger(getClass)

  private implicit val actorSystem = ActorSystem("FixMissingFiles")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)


  lazy val vsConfig = VSConfig(
    uri"${sys.env("VIDISPINE_BASE_URL")}",
    sys.env("VIDISPINE_USER"),
    sys.env("VIDISPINE_PASSWORD")
  )

  def terminate(exitCode:Int) = {
    Await.ready(actorSystem.terminate(), 3 minutes)
    System.exit(exitCode)
  }

  lazy val archiveHunterBaseUri:String = sys.env.get("ARCHIVE_HUNTER_URI") match {
    case None=>
      logger.error("You need to specify ARCHIVE_HUNTER_URI in the environment")
      terminate(1)
      ""  //never executed but included for return value
    case Some(value)=>value
  }

  lazy val archiveHunterSecret:String = sys.env.get("ARCHIVE_HUNTER_SECRET") match {
    case None=>
      logger.error("You need to specify ARCHIVE_HUNTER_SECRET in the environment")
      terminate(1)
      "" //never executed but included for return value
    case Some(value)=>value
  }

  lazy implicit val vsCommunicator = new VSCommunicator(vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass)

  def buildStream() = {
    val sinkFactory = Sink.fold[Seq[ItemReport],ItemReport](Seq())((acc,item)=>acc++Seq(item))

    GraphDSL.create(sinkFactory) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new VSStorageScanSource(None, Some("LOST"), vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass,pageSize=100).async)
      val attachedSwitch = builder.add(new VSFileHasItem)
      val findAssociatedItem = builder.add(new FindAssociatedItem().async)
      val hasArchivePathSwitch = builder.add(new VSItemHasArchivePath)
      val archiveHunterSwitch = builder.add(new CheckArchivehunterSwitch(archiveHunterBaseUri,archiveHunterSecret))
      val sinkMerge = builder.add(Merge[ItemReport](4))

      //val deleteFileSink = builder.add(new DeleteFileSink(reallyDelete=false,failFast=true))
      src.out.log("fix-missing-files-stream") ~> attachedSwitch
      attachedSwitch.out(1).map(f=>VSEntry(Some(f),None,None)).map(entry=>ItemReport(ItemStatus.FileNotAttached, entry)) ~> sinkMerge    //NO branch
      attachedSwitch.out(0) ~> findAssociatedItem ~> hasArchivePathSwitch //YES branch
//
      hasArchivePathSwitch.out(1).map(entry=>ItemReport(ItemStatus.NoArchivePathSet, entry)) ~> sinkMerge
      hasArchivePathSwitch.out(0) ~> archiveHunterSwitch  //YES branch

      archiveHunterSwitch.out(1).map(entry=>ItemReport(ItemStatus.ArchivePathNotValid, entry)) ~> sinkMerge
      archiveHunterSwitch.out(0).map(entry=>ItemReport(ItemStatus.FileArchived, entry)) ~> sinkMerge

      sinkMerge ~> sink
      ClosedShape
    }
  }

  /**
    * gets the file size associated with the original shape of the VSItem in the ItemReport, if one exists
    * @param item ItemReport to query
    * @return an Option which contains the file size, if a valid one exists.
    */
  def getItemSize(item:ItemReport) = {
    val maybeVSItem = item.entry.vsItem

    val maybeOriginalShape = maybeVSItem.flatMap(_.shapes.flatMap(_.get("original")))
    val maybeOriginalShapeFile = maybeOriginalShape.flatMap(_.files.find(_.size>=0))  //skip out invalid file lengths. VS indicates "Couldn't analyze" as -1L.
    maybeOriginalShapeFile.map(_.size)
  }

  def sizeForReport(rpt:Seq[ItemReport]) = rpt.foldLeft[Long](0L)((sum,item)=>sum+getItemSize(item).getOrElse(0L))

  def main(args:Array[String]) = {
    val graph = buildStream()

    val Gb = 1073741824
    RunnableGraph.fromGraph(graph).run().onComplete({
      case Failure(err)=>
        logger.error(s"Stream failed: ", err)
      case Success(reports)=>
        logger.info(s"Processing completed")
        val notAttachedReports = reports.filter(_.status==ItemStatus.FileNotAttached)
        val noPathReports = reports.filter(_.status==ItemStatus.NoArchivePathSet)
        val notValidReports  = reports.filter(_.status==ItemStatus.ArchivePathNotValid)
        val properlyArchivedReports = reports.filter(_.status==ItemStatus.FileArchived)

        val notAttachedSize = sizeForReport(notAttachedReports) / Gb
        val noPathReportsSize = sizeForReport(noPathReports) / Gb
        val notValidReportsSize = sizeForReport(notValidReports) / Gb
        val properlyArchivedReportsSize = sizeForReport(properlyArchivedReports) / Gb

        logger.info(s"Summary status: A total of ${reports.length} files were found that have MISSING files")
        logger.info(s"Summary status: ${notAttachedReports.length} files ($notAttachedSize Gb) were not attached to items, ${noPathReports.length} ($noPathReportsSize Gb) files had no archive paths, ${notValidReports.length} ($notValidReportsSize Gb) files had an archive path not found in Archive Hunter and ${properlyArchivedReports.length} ($properlyArchivedReportsSize Gb) were fully archived.")
        terminate(0)
    })
  }
}
