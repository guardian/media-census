import akka.actor.ActorSystem
import akka.stream.scaladsl.GraphDSL
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import config.VSConfig
import org.slf4j.LoggerFactory
import com.softwaremill.sttp._
import mfstreamcomponents.{DeleteFileSink, FindAssociatedItem, VSFileHasItem, VSItemHasArchivePath}
import streamComponents.VSStorageScanSource
import vidispine.{VSCommunicator, VSEntry}

import scala.concurrent.ExecutionContext.Implicits.global
object FixMissingFiles {
  val logger = LoggerFactory.getLogger(getClass)

  private implicit val actorSystem = ActorSystem("NearlineScanner")
  private implicit val mat:Materializer = ActorMaterializer.create(actorSystem)


  lazy val vsConfig = VSConfig(
    uri"${sys.env("VIDISPINE_BASE_URL")}",
    sys.env("VIDISPINE_USER"),
    sys.env("VIDISPINE_PASSWORD")
  )

  lazy implicit val vsCommunicator = new VSCommunicator(vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass)

  def buildStream() = {
    GraphDSL.create() { implicit builder=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new VSStorageScanSource(None, vsConfig.vsUri, vsConfig.plutoUser, vsConfig.plutoPass,pageSize=100).async)
      val attachedSwitch = builder.add(new VSFileHasItem)
      val findAssociatedItem = builder.add(new FindAssociatedItem().async)
      val hasArchivePathSwitch = builder.add(new VSItemHasArchivePath)

      val deleteFileSink = builder.add(new DeleteFileSink(reallyDelete=false,failFast=true))
      src ~> attachedSwitch
      attachedSwitch.out(1).map(f=>VSEntry(Some(f),None,None)) ~> deleteFileSink     //NO branch
      attachedSwitch.out(0) ~> findAssociatedItem ~> hasArchivePathSwitch //YES branch

      hasArchivePathSwitch.out(1) ~> noPathSink   //NO branch
      hasArchivePathSwitch.out(0) ~> archiveHunterSwitch  //YES branch

      ClosedShape
    }
  }

  def main(args:Array[String]) = {

  }
}
