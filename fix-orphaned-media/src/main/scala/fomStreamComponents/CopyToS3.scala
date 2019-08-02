package fomStreamComponents

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.om.mxs.client.japi.UserInfo
import helpers.{CopyHelper, MatrixStoreHelper}
import org.slf4j.LoggerFactory
import vidispine.VSFile
import models.{CopyStatus, FOMCopyReport, S3Destination}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

class CopyToS3 (userInfo:UserInfo, bucketName:String)(implicit mat:Materializer) extends GraphStage[FlowShape[VSFile,FOMCopyReport]] {
  private final val in:Inlet[VSFile] = Inlet.create("CopyToS3.in")
  private final val out:Outlet[FOMCopyReport] = Outlet.create("CopyToS3.out")

  override def shape = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private val copyHelper = new CopyHelper()

    private val vault = MatrixStoreHelper.openVault(userInfo)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val dest = S3Destination(bucketName, elem.path)

        val failedCb = createAsyncCallback[Throwable](err=>failStage(err))
        val completedCb = createAsyncCallback[FOMCopyReport](rpt=>push(out,rpt))

        MatrixStoreHelper.findByFilename(vault.get, elem.path) match {
          case Failure(err)=>
            logger.error(s"Could not get file from MXS vault:  ", err)
            failStage(err)
          case Success(resultSeq)=>
            if(resultSeq.isEmpty){
              logger.warn(s"Could not get anything in the vault for ${elem.path}")
              push(out, FOMCopyReport(elem, dest,CopyStatus.SOURCE_NOT_FOUND))
            } else {
              if(resultSeq.length>1){
                logger.warn(s"Found ${resultSeq.length} files with name ${elem.path}, expecting only one. Acting on the first.")
                copyHelper.uploadToS3(userInfo,resultSeq.head.oid,bucketName, elem.path).onComplete({
                  case Failure(err)=>
                    logger.error(s"Could not complete copy: ", err)
                    failedCb.invoke(err)
                  case Success(result)=>
                    logger.info(s"Copied data to ${result.bucket}:${result.key} with etag ${result.etag}")
                    completedCb.invoke(FOMCopyReport(elem, dest, CopyStatus.SUCCESS,Some(result.etag)))
                })
              }
            }
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })

    override def preStart(): Unit = {
      if(vault.isFailure){
        logger.error("Could not open MXS vault: ", vault.failed.get)
        failStage(vault.failed.get)
      }
    }
  }
}
