import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.scaladsl.{GraphDSL, Merge, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import config.ESConfig
import models.VSFileIndexer
import io.circe.generic.auto._
import com.sksamuel.elastic4s.circe._
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSFile, VSFileItemMembership, VSFileShapeMembership, VSFileState}
import com.gu.vidispineakka.streamcomponents.VSDeleteFile
import com.om.mxs.client.japi.UserInfo
import streamComponents.IsArchivedSwitch

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.sksamuel.elastic4s.{Hit, HitReader}


object ExfiltratorMain {
  implicit lazy val actorSystem:ActorSystem = ActorSystem("exfiltrator")
  implicit lazy val mat:Materializer = ActorMaterializer.create(actorSystem)

  val indexName = sys.env("NEARLINE_INDEX")
  val nearlineIndexer = new VSFileIndexer(indexName)

  lazy val esConfig = ESConfig(
    sys.env.get("ES_URI"),
    sys.env.getOrElse("ES_HOST","mediacensus-elasticsearch"),
    sys.env.getOrElse("ES_PORT","9200").toInt
  )

  lazy val reallyDelete = sys.env.get("REALLY_DELETE").contains("true")

  def getVSCommunicator = {
    import com.softwaremill.sttp._

    val maybeVsUri = sys.env.get("VS_URI").map(uriString=>UriInterpolator.interpolate(StringContext(uriString)))
    if(maybeVsUri.isEmpty) {
      throw new RuntimeException("You must specify VS_URI in the environment")
    }

    new VSCommunicator(maybeVsUri.get, sys.env.getOrElse("VS_USER", "admin"), sys.env.getOrElse("VS_PASSWD", ""))
  }
  implicit lazy val vsComm:VSCommunicator = getVSCommunicator

  def getEsClient = Try {
    val uri = esConfig.uri match {
      case Some(uri)=>uri
      case None=>s"http://${esConfig.host}:${esConfig.port}"
    }

    ElasticClient(ElasticProperties(uri))
  }

  def getUserInfo = UserInfoBuilder.fromFile(sys.env("MXS_VAULT"))
  val userInfo:UserInfo = getUserInfo.get  //allow it to raise if we can't load the file

  val uploader = new Uploader(userInfo,sys.env("ARCHIVE_BUCKET"))

  def makeStream(esClient:ElasticClient) = {
    val finalSink = Sink.ignore

    /*
    looks like the package confusion is preventing the auto-derivation from working :(
     */
    implicit object VSFileHitReader extends HitReader[VSFile] {
      override def read(hit: Hit): Try[VSFile] = Try {
        val src = hit.sourceAsMap
        val maybeMembership = src.get("membership")
          .map(_.asInstanceOf[Map[String, Any]])
          .map(memsrc=>
            VSFileItemMembership(
              memsrc("itemid").asInstanceOf[String],
              memsrc("shapes")
                .asInstanceOf[Seq[Map[String,Any]]]
                .map(shapesrc=>VSFileShapeMembership(
                  shapesrc("shapeId").asInstanceOf[String],
                  shapesrc("componentid").asInstanceOf[Seq[String]]
                ))
            )
          )

        VSFile(
          src("vsid").asInstanceOf[String],
          src("path").asInstanceOf[String],
          src("uri").asInstanceOf[String],
          src.get("state").map(_.asInstanceOf[String]).map(s=>VSFileState.withName(s)),
          src("size").asInstanceOf[Long],
          src.get("hash").map(_.asInstanceOf[String]),
          ZonedDateTime.parse(src("timestamp").asInstanceOf[String]),
          src("refreshFlag").asInstanceOf[Int],
          src("storage").asInstanceOf[String],
          src.get("metadata").map(_.asInstanceOf[Map[String,String]]),
          maybeMembership,
          src.get("archiveHunterId").map(_.asInstanceOf[String]),
          src.get("archiveHunterConflict").map(_.asInstanceOf[Boolean])
        )
      }
    }

    GraphDSL.create(finalSink) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      import com.sksamuel.elastic4s.http.ElasticDsl._

      val src = nearlineIndexer.getSource(esClient, Seq(), None)
      val deletionRequestBuilder:RequestBuilder[VSFile] = (t: VSFile) => delete(t.vsid) from s"$indexName/vsfile"
      val deleteRecord = nearlineIndexer.deleteSinkCustom(esClient,
        reallyDelete, deletionRequestBuilder)

      val isArchivedSwitch = builder.add(new IsArchivedSwitch)
      val deletionMerge = builder.add(Merge[VSFile](2))
      val vsDeleteFile = builder.add(new com.gu.vidispineakka.streamcomponents.VSDeleteFile(reallyDelete))
      val finalMerge = builder.add(Merge[VSFile](3))

      src.map(_.to[VSFile]) ~> isArchivedSwitch

      //YES branch - it's archived - delete the files
      isArchivedSwitch.out(0) ~> deletionMerge

      //NO branch - it's not archived - upload it. Upload errors (not ignores) will terminate the stream.
      isArchivedSwitch.out(1).mapAsync(4)(uploader.handleUnarchivedFile) ~> deletionMerge
      isArchivedSwitch.out(2) ~> finalMerge  //CONFLICT branch

      deletionMerge ~> vsDeleteFile ~> deleteRecord //this is a sink
      ClosedShape
    }


  }

  def main(args:Array[String]) = {

  }
}
