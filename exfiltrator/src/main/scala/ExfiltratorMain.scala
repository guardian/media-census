import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.scaladsl.{GraphDSL, Merge, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import com.gu.vidispineakka.streamcomponents.VSGetItem
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties, RequestFailure, RequestSuccess}
import config.ESConfig
import models.VSFileIndexer
import io.circe.generic.auto._
import com.sksamuel.elastic4s.circe._
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSFile, VSFileItemMembership, VSFileShapeMembership, VSFileState}
import com.om.mxs.client.japi.UserInfo
import com.sksamuel.elastic4s.http.search.SearchResponse
import streamComponents.{DoesFileExist, ExfiltratorStreamElement, IsArchivedSwitch, IsWithinProjectSwitch, UploadStreamComponent, VSDelete, ValidStatusSwitch}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.sksamuel.elastic4s.{Hit, HitReader}
import helpers.TrustStoreHelper
import org.slf4j.LoggerFactory
import utils.{Uploader, VSProjects}

import scala.concurrent.Future


object ExfiltratorMain {
  private val logger = LoggerFactory.getLogger(getClass)
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

  val localTrustStore = sys.env.get("LOCAL_TRUST_STORE")
  if(localTrustStore.isDefined) {
    logger.info(s"Adding local trust store at ${localTrustStore.get}")
    TrustStoreHelper.setupTS(Seq(localTrustStore.get)) match {
      case Success(context)=>
        Http().setDefaultClientHttpsContext(ConnectionContext.https(context))
      case Failure(err)=>
        logger.error("Could not set up local trust store: ", err)
        sys.exit(1)
    }
  }

  val potentialMediaBuckets = sys.env.get("MEDIA_BUCKETS") match {
    case None=>
      logger.error("You must specify MEDIA_BUCKETS in the environment")
      sys.exit(1)
    case Some(mediaBuckets)=>
      mediaBuckets.split("\\s*,\\s*")
  }

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

  val uploader = new Uploader(userInfo,sys.env("ARCHIVE_BUCKET"), sys.env("PROXY_BUCKET"), potentialMediaBuckets, reallyDelete)

  val storageId = sys.env("STORAGE")

  val interestingItemFields = Seq(
    "gnm_storage_rule_sensitive",
    "title",
    "gnm_category"
  )

  def countEstimate(esClient:ElasticClient) = {
    import com.sksamuel.elastic4s.http.ElasticDsl._
    import com.sksamuel.elastic4s.circe._
    import io.circe.generic.auto

    esClient.execute {
      search(indexName).query(matchQuery("storage", storageId))
    }.map({
      case RequestFailure(status, body, headers, error)=>
        logger.error(s"Could not perform initial check: $status $body")
        throw new RuntimeException("Elasticsearch server error")
      case success:RequestSuccess[SearchResponse]=>
        success.result.totalHits
      case success:RequestSuccess[_]=>
        logger.error(s"unexpected result of type ${success.getClass.toGenericString}")
        throw new RuntimeException("invalid response")
    })
  }

  def countStream(esClient:ElasticClient) = {
    val finalSink = Sink.fold[Long, Any](0)((acc,elem)=>acc+1)

    val graph = GraphDSL.create(finalSink) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      import com.sksamuel.elastic4s.http.ElasticDsl._
      import utils.VSFileHitReader._

      val src = nearlineIndexer.getSource(esClient, Seq(matchQuery("storage",storageId)), None)

      val isArchivedSwitch = builder.add(new IsArchivedSwitch)
      val validStatusSwitch = builder.add(new ValidStatusSwitch)

     // val ignoreMerge = builder.add(Merge[VSFile](4))
      val ignoreSink = builder.add(Sink.ignore)

      val countMerge = builder.add(Merge[Any](3))
      src.map(_.to[VSFile]) ~> validStatusSwitch

      //YES branch - it's valid - pass it on
      validStatusSwitch.out(0) ~> isArchivedSwitch
      //NO branch - it's not valid - log and ignore
      validStatusSwitch.out(1).map(elem=>{
        logger.info(s"Dropping file ${elem.vsid} because status is ${elem.state}")
        elem
      }) ~> ignoreSink

      //YES branch - it's archived - delete the files
      isArchivedSwitch.out(0).map(file=>ExfiltratorStreamElement(file, None)) ~> countMerge

      //NO branch - it's not archived - archive the files
      isArchivedSwitch.out(1) ~> countMerge

      //CONFLICT branch - count it anyway
      isArchivedSwitch.out(2) ~> countMerge
      countMerge ~> sink
      ClosedShape
    }

    RunnableGraph.fromGraph(graph).run().map(count=>{
      logger.info(s"Test stream processed $count items")
    })
  }

  def makeStream(esClient:ElasticClient, sensitiveProjects:Seq[String], deletableProjects:Seq[String]) = {
    val finalSink = Sink.ignore

    GraphDSL.create(finalSink) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._
      import com.sksamuel.elastic4s.http.ElasticDsl._
      import utils.VSFileHitReader._

      val src = nearlineIndexer.getSource(esClient, Seq(matchQuery("storage",storageId)), None)
      val deletionRequestBuilder:RequestBuilder[VSFile] = (t: VSFile) => delete(t.vsid) from s"$indexName/vsfile"
      val deleteRecord = nearlineIndexer.deleteSinkCustom(esClient,
        reallyDelete, deletionRequestBuilder)

      val doesExistSwitch = builder.add(new DoesFileExist)
      val isArchivedSwitch = builder.add(new IsArchivedSwitch)
      val validStatusSwitch = builder.add(new ValidStatusSwitch)
      val isProjectSensitiveSwitch = builder.add(new IsWithinProjectSwitch(sensitiveProjects, "sensitive projects"))
      val isProjectDeletableSwitch = builder.add(new IsWithinProjectSwitch(deletableProjects, "deletable projects"))

      val deletionMerge = builder.add(Merge[ExfiltratorStreamElement](2))
      val itemLookup = builder.add(new VSGetItem(interestingItemFields, includeShapes=true))

      val ignoreMerge = builder.add(Merge[VSFile](5))
      val vsDeleter = builder.add(new VSDelete(reallyDelete))
      val uploadStage = builder.add(new UploadStreamComponent(uploader))

      src.map(_.to[VSFile]).async ~> doesExistSwitch

      //YES branch - it does exist - pass it on
      doesExistSwitch.out(0) ~> validStatusSwitch
      //NO branch - it does not exist - ignore
      doesExistSwitch.out(1) ~> ignoreMerge

      //YES branch - it's valid - pass it on
      validStatusSwitch.out(0) ~> isArchivedSwitch
      //NO branch - it's not valid - log and ignore
      validStatusSwitch.out(1).map(elem=>{
        logger.info(s"Dropping file ${elem.vsid} because status is ${elem.state}")
        elem
      }) ~> ignoreMerge

      //YES branch - it's archived - delete the files
      isArchivedSwitch.out(0).map(file=>ExfiltratorStreamElement(file, None)) ~> deletionMerge

      //NO branch - it's not archived - upload it. Upload errors (not ignores) will terminate the stream; ignored files
      //will pull the next file
      isArchivedSwitch.out(1) ~> itemLookup
      itemLookup.out.map(fileItemTuple=>ExfiltratorStreamElement(fileItemTuple._1, fileItemTuple._2)) ~> isProjectSensitiveSwitch

      //YES branch - project is sensitive - leave it
      isProjectSensitiveSwitch.out(0).map(_.file) ~> ignoreMerge
      //NO branch - project is not sensitive - continue
      isProjectSensitiveSwitch.out(1) ~> isProjectDeletableSwitch

      //YES branch - project is deletable - hmmmm
      isProjectDeletableSwitch.out(0).map(_.file) ~> ignoreMerge
      //NO branch - project is not deletable - upload it. If the upload fails, then uploadStage does not forward on the processed element but pulls in another
      isProjectDeletableSwitch.out(1) ~> uploadStage ~> deletionMerge

      isArchivedSwitch.out(2) ~> ignoreMerge  //CONFLICT branch
      ignoreMerge ~> sink

      deletionMerge ~> vsDeleter      //vsDeleter deletes the item if there is one (implying deletion of the files) and deletes the file if there is not
      vsDeleter.out.map(_.file) ~> deleteRecord //this is a sink
      ClosedShape
    }
  }

  def main(args:Array[String]) = {

    getEsClient match {
      case Failure(err) =>
        logger.error(s"Could not set up ES client: $err")
        sys.exit(1)
      case Success(esClient) =>
        countEstimate(esClient).flatMap(expectedCount=>{
          logger.info(s"Expecting $expectedCount records to be returned")

          Future.sequence(Seq(
            VSProjects.findSensitiveProjectIds,
            VSProjects.findDeletableProjectIds
          )).flatMap(projectIdLists => {
            val sensitiveProjectIds = projectIdLists.head
            val deletableProjectIds = projectIdLists(1)

            logger.info(s"Found ${sensitiveProjectIds.length} sensitive projects and ${deletableProjectIds.length} deletable projects")
            if (sensitiveProjectIds.length < 2) {
              Future.failed(new RuntimeException("Not enough projects found, this probably indicates a bug"))
            } else {
              val stream = makeStream(esClient, sensitiveProjectIds, deletableProjectIds)
              RunnableGraph.fromGraph(stream).run()
              //countStream(esClient)
            }
          })
        }).onComplete({
          case Success(_) =>
            logger.info("Run completed successfully")
            sys.exit(0)
          case Failure(err) =>
            logger.error(s"Run terminated abnormally", err)
            sys.exit(1)
        })
    }
  }
}
