package fomStreamComponents

import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.slf4j.LoggerFactory
import vidispine.VSFile

import scala.util.{Failure, Success, Try}

/**
  * this implements a graph stage which pushes its input to the "yes" port if it does exist in S3 or to the "no" port if
  * it doesn't.  It assumes that AWS credentials and region are provided in the OS environment
  */
class ExistsInS3Switch(inBuckets:List[String]) extends GraphStage[UniformFanOutShape[VSFile,VSFile]] {
  private final val in:Inlet[VSFile] = Inlet.create("ExistsInS3Switch.in")
  private final val yes:Outlet[VSFile] = Outlet.create("ExistsInS3Switch.yes")
  private final val no:Outlet[VSFile] = Outlet.create("ExistsInS3Switch.no")

  override def shape: UniformFanOutShape[VSFile, VSFile] = UniformFanOutShape(in, yes, no)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private val s3Client = AmazonS3ClientBuilder.defaultClient()

    private var ctr:Int=0

    def checkInBucket(elem:VSFile, forBucket:String, remainingBuckets:List[String]):Try[Boolean] = {
      val result = Try { s3Client.doesObjectExist(forBucket, elem.path) }

      result match {
        case Success(true)=>
          try {
            val meta = s3Client.getObjectMetadata(forBucket, elem.path)

            logger.debug(s"[$ctr] ${elem.vsid} (${elem.path}) exists in S3 bucket $forBucket with size ${meta.getContentLength}, local size ${elem.size}")
            if (elem.size != meta.getContentLength) { //if sizes don't match count as not present, we then create another copy anyway
              logger.warn(s"[$ctr] ${elem.vsid} (${elem.path}) remote copy size does not match")
              Success(false)
            } else {
              result
            }
          } catch {
            case err:Throwable=>
              logger.error(s"Size check failed: ", err)
              Failure(err)
          }
        case Success(false)=>
          logger.debug(s"[$ctr] ${elem.vsid} (${elem.path}) does not exist in S3 bucket $forBucket")
          if(remainingBuckets.nonEmpty){
            checkInBucket(elem,remainingBuckets.head, remainingBuckets.tail)
          } else {
            //we got to the end of the list, not found anywhere
            Success(false)
          }
        case Failure(err)=>
          logger.error(s"[$ctr] Could not check ${elem.vsid} (${elem.path}) in S3: ", err)
          result
      }
    }

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        ctr+=1
        val result = checkInBucket(elem, inBuckets.head, inBuckets.tail)

        result match {
          case Success(true)=>
            push(yes, elem)
          case Success(false)=>
            push(no, elem)
          case Failure(err)=>
            failStage(err)
        }
      }
    })

    val commonOutHandler = new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    }
    setHandler(yes, commonOutHandler)
    setHandler(no, commonOutHandler)
  }
}