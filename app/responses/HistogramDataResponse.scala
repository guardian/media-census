package responses

import models.StatsEntry
import org.slf4j.LoggerFactory

import scala.util.Try

case class HistogramDataResponse[T:io.circe.Encoder,V:io.circe.Encoder,N:io.circe.Encoder] (status:String, buckets:List[T], values:List[V], totalSize:List[V], extraData:Option[N])

object HistogramDataResponse {
  private val logger = LoggerFactory.getLogger(getClass)

  def apply[T:io.circe.Encoder,V:io.circe.Encoder](status: String, buckets: List[T], values: List[V], totalSize:List[V]): HistogramDataResponse[T,V,Unit] =
    new HistogramDataResponse(status, buckets, values, totalSize, None)

  def fromEsData[T:io.circe.Encoder,V:io.circe.Encoder](data:Map[String, Any]):Try[HistogramDataResponse[T,V,Unit]] = Try {
    logger.debug(s"fromEsData: incoming data is $data")
    val keyData = data("buckets").asInstanceOf[List[Map[String,Any]]]

    new HistogramDataResponse[T,V,Unit]("ok",
      keyData.map(entry=>entry("key").asInstanceOf[T]),
      keyData.map(entry=>entry("doc_count").asInstanceOf[V]),
      List(),
      None
    )
  }

  def fromMap[T:io.circe.Encoder,V:io.circe.Encoder,N:io.circe.Encoder](data:Map[T,V], extraData:Option[N]) = {
    new HistogramDataResponse[T,V,N]("ok",data.keys.toList, data.values.toList, List(), extraData)
  }

  def fromStatsEntries[T:io.circe.Encoder](data:List[StatsEntry], extraData:Option[T]) = {
    new HistogramDataResponse[String,Long,T]("ok",data.map(_.key),data.map(_.count),data.map(_.totalSize), extraData)
  }
}
