package helpers

import akka.stream.{ClosedShape, Materializer}
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{GraphDSL, RunnableGraph}
import com.om.mxs.client.japi.UserInfo
import streamcomponents.MatrixStoreFileSource

import scala.concurrent.ExecutionContext.Implicits.global

class CopyHelper(implicit mat:Materializer){
  /** generates a graph to upload files to S3 from ObjectMatrix */
  def uploadToS3Graph(userInfo:UserInfo,oid:String, destBucket:String, destName:String) = {
    val sinkFactory = S3.multipartUpload(destBucket, destName)

    GraphDSL.create(sinkFactory) { implicit builder =>sink =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new MatrixStoreFileSource(userInfo, oid).async)

      src ~> sink
      ClosedShape
    }
  }

  /**
    * performs an S3 upload via akka stream
    * @param userInfo login info for ObjectMatrix
    * @param oid ID of the objectmatrix file to upload
    * @param destBucket bucket that we want to upload to
    * @param destName name of the file to upload in the bucket
    * @return a Future, containing a MultipartUploadResult.
    */
  def uploadToS3(userInfo:UserInfo,oid:String, destBucket:String, destName:String) =
    RunnableGraph.fromGraph(uploadToS3Graph(userInfo,oid, destBucket, destName)).run()
}
