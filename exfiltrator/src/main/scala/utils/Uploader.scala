package utils

import akka.actor.ActorSystem
import akka.stream.alpakka.s3.MultipartUploadResult
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{GraphDSL, Keep, RunnableGraph, Sink}
import akka.stream.{ClosedShape, Materializer}
import com.gu.vidispineakka.streamcomponents.VSFileContentSource
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSFile, VSFileState, VSLazyItem}
import com.om.mxs.client.japi.UserInfo
import org.slf4j.LoggerFactory
import streamComponents.ExfiltratorStreamElement
import streamcomponents.MatrixStoreFileSource

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

/**
 * Utility class that performs item checking and uploads
 * @param userInfo ObjectMatrix UserInfo instance that describes the OM appliance to lift data from
 * @param mediaBucket S3 bucket for deep archive
 * @param proxyBucket S3 bucket for proxies corresponding to the deep archive
 * @param actorSystem implicitly provided ActorSystem
 * @param mat implicitly provided Materializer
 * @param vsComm implicitly provided VSCommunicator
 */
class Uploader (userInfo:UserInfo, mediaBucket:String, proxyBucket:String, allowUploads:Boolean=true)(implicit actorSystem: ActorSystem, mat:Materializer, vsComm:VSCommunicator) {
  private val logger = LoggerFactory.getLogger(getClass)

  val interestingFields = Seq("gnm_storage_rule_sensitive","gnm_category")
  val potentialProxies = Seq("lowres","lowaudio","lowimage")

  /**
   * extension method to turn Option[Future] into Future[Option]
   * https://stackoverflow.com/questions/38226203/scala-optionfuturet-to-futureoptiont
   * @param f
   * @tparam A
   */
  implicit class OptionSwitch[A](f: Option[Future[A]]) {
    def switchToFut: Future[Option[A]] = Future.sequence(Option.option2Iterable(f))
      .map(_.headOption)
  }

  def s3Exists(destBucket: String, destPath: String) = {
    S3.getObjectMetadata(destBucket, destPath)
      .toMat(Sink.head)(Keep.right)
      .run()
      .map(_.isDefined) //documentation states that if object does not exist then None is returned
  }

  /**
   * copy a given file from the objectmatrix appliance to the deep archive
   * @param vsFile a VSFile instance
   * @return a Future containing a MultipartUploadResult
   */
  def doOMUpload(vsFile:VSFile, omId:String, destBucket:String):Future[Option[MultipartUploadResult]] = {
    val destPath = vsFile.path
    val s3Sink = S3.multipartUpload(destBucket, destPath)

    s3Exists(destBucket, destPath).flatMap({
      case true =>
        logger.info(s"s3://$destBucket/$destPath already exists, not over-writing")
        Future(None)
      case false =>
        if(allowUploads) {
          val stream = GraphDSL.create(s3Sink) { implicit builder =>
            sink =>
              import akka.stream.scaladsl.GraphDSL.Implicits._
              val src = builder.add(new MatrixStoreFileSource(userInfo, omId))
              src ~> sink
              ClosedShape
          }
          RunnableGraph.fromGraph(stream).run().map(r => Some(r))
        } else {
          Future(None)
        }
    })
  }

  /**
   * copy a given file to deep archive by streaming it out of VS
   * @param vsFile a VSFile instance
   * @return a Future containing a MultipartUploadResult
   */
  def doVSUpload(vsFile:com.gu.vidispineakka.vidispine.VSFile, destBucket:String):Future[Option[MultipartUploadResult]] = {
    val destPath = vsFile.path
    val s3Sink = S3.multipartUpload(destBucket, destPath)

    s3Exists(destBucket, destPath).flatMap({
      case true=>
        logger.info(s"s3://$destBucket/$destPath already exists, not over-writing")
        Future(None)
      case false=>
        VSFileContentSource.sourceFor(vsFile).flatMap({
          case Left(errString) =>
            logger.error(s"Could not get file source for ${vsFile.uri} (${vsFile.vsid}): $errString")
            throw new RuntimeException(errString)
          case Right(vsSource) =>
            if(allowUploads) {
              val stream = GraphDSL.create(s3Sink) { implicit builder =>
                sink =>
                  import akka.stream.scaladsl.GraphDSL.Implicits._
                  val src = builder.add(vsSource)
                  src ~> sink
                  ClosedShape
              }
              RunnableGraph.fromGraph(stream).run().map(r => Some(r))
            } else {
              Future(None)
            }
        })
    })
  }

  /**
   * check the 'is sensitive' field. Return true if it is sensitive or false it it isn't/
   * @return boolean indicator
   */
  def isSensitive(item:VSLazyItem):Boolean =
      item.get("gnm_storage_rule_sensitive") match {
        case None=>false
        case Some(values)=>
          val nonEmptyValues = values.filter(_.length>0)
          nonEmptyValues.nonEmpty
      }


  /**
   * check if the 'category' field says this is a master or deliverable. Return true if so or false if it isn't
   */
  def isMaster(item:VSLazyItem):Boolean =
      item.get("gnm_category") match {
        case None=>false
        case Some(values)=>
          val nonEmptyValues = values.filter(_.length>0)
          val catsToFilter = Seq("Master","Deliverable")
          nonEmptyValues.intersect(catsToFilter).nonEmpty
      }

  /**
   * finds VSFile instances for all shape tags marked as proxies
   * @param maybeItem an Option containing a populated VSLazyItem
   * @return a (possibly empty) sequence of VSFile instances
   */
  def findProxy(item:VSLazyItem):Seq[VSFile] = item.shapes.map(allShapes=>{
      val proxyShapes = potentialProxies.filter(shapetag=>allShapes.contains(shapetag))
      logger.info(s"${item.itemId}: Found ${proxyShapes.length} proxies: ${proxyShapes}")

      proxyShapes.map(shapetag=>{
        allShapes(shapetag).files.headOption
      }).collect({case Some(file)=>file})
    }).getOrElse(Seq())

  /**
   * check the metadata dictionary for potential uuids
   * @param maybeFile an Option containing a VSFile
   * @return an Option contiaining the uuid if (a) the VSItem exists (b) it has a metadata dictionary (c) the metadata
   *         dictionary contains at least one uuid field
   */
  def findObjectMatrixId(maybeFile:Option[VSFile]):Option[String] = {
    val maybeMeta = for {
      file <- maybeFile
      meta <- file.metadata
    } yield meta

    maybeMeta.flatMap(meta=>{
      val potentialValues:Seq[Option[String]] = Seq(meta.get("uuid"), meta.get("meta_uuid_s"))
      potentialValues.collectFirst { case Some(id) => id }
    })
  }

  def performUploads(elem:ExfiltratorStreamElement, zeroOrMoreProxies:Seq[VSFile]) = {
    val allFilesList = Seq(elem.file) ++ zeroOrMoreProxies
    logger.info(s"${elem.maybeItem.map(_.itemId)}: ${allFilesList.length} files to upload")

    //allow VS uploads to fail with a logged error and not abort the run.
    //this means that we need to have an Option in our sequence; to keep the rest of the logic in-place
    //we do a final map() on the future to filter out any None values
    //i.e. allFilesList (Seq[VSFile]) is mapped to Seq[Future[Option[MultipartUploadResult]]]
    //then Future.sequence is applied to make Future[Seq[Option[MultipartUploadResult]]]
    //then collect is applied to the sequence in a future-map to make Future[Seq[MultipartUploadResult]], i.e.
    //just the results of the uploads.
    Future.sequence(allFilesList.map(fileToUpload=>{
      val destBucket = if(fileToUpload==elem.file) mediaBucket else proxyBucket
      val maybeOMId = findObjectMatrixId(Some(fileToUpload))

      logger.info(s"attempting to upload file '${fileToUpload.vsid}' at '${fileToUpload.path}'")
      maybeOMId match {
        case Some(omId)=>
          doOMUpload(fileToUpload, omId, destBucket)
        case None=>
          doVSUpload(fileToUpload, destBucket)
            .recover({
              case _:Throwable=>
                logger.error(s"File ${fileToUpload.vsid} (${fileToUpload.uri}) was not uploaded!")
                None
            })
      }
    })).map(_.collect({case Some(result)=>result}))
  }

  /**
   * main function of this helper.
   * Takes a VSFile from the index, and looks up the associated item.
   * If the item contains the "sensitive" flag, logs a message and does nothing
   * If the item is not sensitive then a list is made of all the VSFiles that represent proxies (as per the configured
   * list at the top of the class)
   * A list is then made of the incoming file plus any proxies found (the incoming file only if there is no associated item)
   * and they are pushed to deep archive in parallel.
   *
   * @param elem ExfiltratorStreamElement with a reference to a VSFile entry to load and possibly its item
   * @return a Future, containing a sequence of MultipartUploadResults, one for each uploaded file.
   */
  def handleUnarchivedFile(elem:ExfiltratorStreamElement):Future[Seq[MultipartUploadResult]] = {
    val file = elem.file
    if(file.state.contains(VSFileState.LOST)) {
      logger.warn(s"File ${file.vsid} is Lost, ignoring")
      return Future(Seq())
    }

    if(file.membership.isEmpty) {
      logger.info(s"Found lone file: $file")
    }

    elem.maybeItem match {
      case Some(item)=>
        if(isSensitive(item)) {
          logger.warn(s"Item ${item.itemId} is flagged as sensitive, leaving alone")
          Future(Seq())
        } else if(isMaster(item)) {
          logger.warn(s"Item ${item.itemId} is a master or deliverable, leaving alone")
          Future(Seq())
        } else {
          performUploads(elem, findProxy(item))
        }
      case None=>
        performUploads(elem, Seq())
    }
  }
}
