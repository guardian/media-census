package responses

import org.slf4j.LoggerFactory

import scala.util.Try

case class HistogramDataResponse[T:io.circe.Encoder,V:io.circe.Encoder] (status:String, buckets:List[T], values:List[V])

object HistogramDataResponse {
  private val logger = LoggerFactory.getLogger(getClass)

  def apply[T:io.circe.Encoder,V:io.circe.Encoder](status: String, buckets: List[T], values: List[V]): HistogramDataResponse[T,V] =
    new HistogramDataResponse(status, buckets, values)

  def fromEsData[V:io.circe.Encoder](data:Map[String, Any]):Try[HistogramDataResponse[String,V]] = Try {
    logger.debug(s"fromEsData: incoming data is $data")
    new HistogramDataResponse[String, V]("ok",
      data("buckets").asInstanceOf[List[String]],
      data("values").asInstanceOf[List[V]]
    )
  }
}
