package responses

import org.slf4j.LoggerFactory

import scala.util.Try

case class HistogramDataResponse[T:io.circe.Encoder,V:io.circe.Encoder] (status:String, buckets:List[T], values:List[V])

object HistogramDataResponse {
  private val logger = LoggerFactory.getLogger(getClass)

  def apply[T:io.circe.Encoder,V:io.circe.Encoder](status: String, buckets: List[T], values: List[V]): HistogramDataResponse[T,V] =
    new HistogramDataResponse(status, buckets, values)

  def fromEsData[T:io.circe.Encoder,V:io.circe.Encoder](data:Map[String, Any]):Try[HistogramDataResponse[T,V]] = Try {
    logger.debug(s"fromEsData: incoming data is $data")
    val keyData = data("buckets").asInstanceOf[List[Map[String,Any]]]

    new HistogramDataResponse[T,V]("ok",
      keyData.map(entry=>entry("key").asInstanceOf[T]),
      keyData.map(entry=>entry("doc_count").asInstanceOf[V])
    )
  }
}
