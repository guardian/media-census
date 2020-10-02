package utils

import akka.actor.ActorSystem
import akka.stream.alpakka.s3.MultipartUploadResult
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{GraphDSL, RunnableGraph}
import akka.stream.{ClosedShape, Materializer}
import com.gu.vidispineakka.streamcomponents.VSFileContentSource
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSFile, VSFileState, VSLazyItem}
import com.om.mxs.client.japi.UserInfo
import org.slf4j.LoggerFactory
import streamcomponents.MatrixStoreFileSource

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

/**
 * Utility class that performs item checking and uploads
 * @param userInfo ObjectMatrix UserInfo instance that describes the OM appliance to lift data from
 * @param destBucket S3 bucket for deep archive
 * @param actorSystem implicitly provided ActorSystem
 * @param mat implicitly provided Materializer
 * @param vsComm implicitly provided VSCommunicator
 */
class Uploader (userInfo:UserInfo, mediaBucket:String, proxyBucket:String)(implicit actorSystem: ActorSystem, mat:Materializer, vsComm:VSCommunicator) {
  private val logger = LoggerFactory.getLogger(getClass)

  val interestingFields = Seq("gnm_storage_rule_sensitive")
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


  /**
   * copy a given file from the objectmatrix appliance to the deep archive
   * @param vsFile a VSFile instance
   * @return a Future containing a MultipartUploadResult
   */
  def doOMUpload(vsFile:VSFile, destBucket:String):Future[MultipartUploadResult] = {
    val destPath = vsFile.path
    val s3Sink = S3.multipartUpload(destBucket, destPath)

    vsFile.metadata.flatMap(_.get("uuid")) match {
      case Some(omId) =>
        val stream = GraphDSL.create(s3Sink) { implicit builder =>
          sink =>
            import akka.stream.scaladsl.GraphDSL.Implicits._
            val src = builder.add(new MatrixStoreFileSource(userInfo, omId))
            src ~> sink
            ClosedShape
        }

        RunnableGraph.fromGraph(stream).run()
      case None =>
        logger.error(s"File ${vsFile.vsid} (${vsFile.uri}) has no 'uuid' metadata: ${vsFile.metadata}")
        Future.failed(new RuntimeException("No uuid"))
    }
  }

  /**
   * copy a given file to deep archive by streaming it out of VS
   * @param vsFile a VSFile instance
   * @return a Future containing a MultipartUploadResult
   */
  def doVSUpload(vsFile:com.gu.vidispineakka.vidispine.VSFile, destBucket:String):Future[MultipartUploadResult] = {
    val destPath = vsFile.path
    val s3Sink = S3.multipartUpload(destBucket, destPath)

    VSFileContentSource.sourceFor(vsFile).flatMap({
      case Left(errString)=>
        logger.error(s"Could not get file source for ${vsFile.uri} (${vsFile.vsid}): $errString")
        throw new RuntimeException(errString)
      case Right(vsSource)=>
        val stream = GraphDSL.create(s3Sink) {implicit builder=> sink=>
          import akka.stream.scaladsl.GraphDSL.Implicits._
          val src = builder.add(vsSource)
          src ~> sink
          ClosedShape
        }
        RunnableGraph.fromGraph(stream).run()
    })
  }

  /**
   * check the 'is sensitive' field. Return true if it is sensitive or false it it isn't/
   * @param maybeItem an Option containing a populated VSLazyItem
   * @return boolean indicator
   */
  def isSensitive(maybeItem:Option[VSLazyItem]):Boolean = maybeItem match {
    case Some(item)=>
      item.get("gnm_storage_rule_sensitive") match {
        case None=>false
        case Some(values)=>
          val nonEmptyValues = values.filter(_.length>0)
          nonEmptyValues.nonEmpty
      }
    case None=> false
  }

  /**
   * finds VSFile instances for all shape tags marked as proxies
   * @param maybeItem an Option containing a populated VSLazyItem
   * @return a (possibly empty) sequence of VSFile instances
   */
  def findProxy(maybeItem:Option[VSLazyItem]):Seq[VSFile] = maybeItem.flatMap(item=>item.shapes.map(allShapes=>{
      val proxyShapes = potentialProxies.filter(shapetag=>allShapes.contains(shapetag))
      logger.info(s"${item.itemId}: Found ${proxyShapes.length} proxies: ${proxyShapes}")

      proxyShapes.map(shapetag=>{
        allShapes(shapetag).files.headOption
      }).collect({case Some(file)=>file})
    })
  ).getOrElse(Seq())

  /**
   * main function of this helper.
   * Takes a VSFile from the index, and looks up the associated item.
   * If the item contains the "sensitive" flag, logs a message and does nothing
   * If the item is not sensitive then a list is made of all the VSFiles that represent proxies (as per the configured
   * list at the top of the class)
   * A list is then made of the incoming file plus any proxies found (the incoming file only if there is no associated item)
   * and they are pushed to deep archive in parallel.
   *
   * @param file VSFile entry to load
   * @return a Future, containing a sequence of MultipartUploadResults, one for each uploaded file.
   */
  def handleUnarchivedFile(file:VSFile):Future[Seq[MultipartUploadResult]] = {
    if(file.state.contains(VSFileState.LOST)) {
      logger.warn(s"File ${file.vsid} is Lost, ignoring")
      return Future(Seq())
    }

    val maybeItem = file.membership.map(m=>new VSLazyItem(m.itemId))

    val itemMetadataFut = maybeItem.map(item=>item.getMoreMetadata(interestingFields).map({
      case Left(err) =>
        logger.error(s"Could not load metadata from ${item.itemId}: ${err.toString}")
        throw new RuntimeException("Could not load metadata") //fail the future
      case Right(updatedItem)=>
        updatedItem
    })).switchToFut.map(_.toRight())

    val supplementaryFiles:Future[Either[Unit, Seq[VSFile]]] = itemMetadataFut.map(maybeItem=>{
      if(isSensitive(maybeItem.toOption)) {
        logger.warn(s"Item ${maybeItem.map(_.itemId)} is flagged as sensitive, leaving alone")
        Left( () )
      } else {
        Right(findProxy(maybeItem.toOption))
      }
    })

    supplementaryFiles.flatMap({
      case Left(_)=>  //Left => we should not continue
        Future(Seq())
      case Right(zeroOrMoreProxies)=> //Right => we should continue
        val allFilesList = Seq(file) ++ zeroOrMoreProxies
          logger.info(s"${maybeItem.map(_.itemId)}: ${allFilesList.length} files to upload")

        Future.sequence(allFilesList.map(fileToUpload=>{
          val destBucket = if(fileToUpload==file) mediaBucket else proxyBucket
          if(fileToUpload.uri.startsWith("omms")) {
            doOMUpload(fileToUpload, destBucket)
          } else {
            doVSUpload(fileToUpload, destBucket)
          }
        }))
    })

  }
}
