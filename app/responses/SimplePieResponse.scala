package responses

import com.sksamuel.elastic4s.http.search.Aggregations

case class SimplePieResponseEntry(label: String, value: Int)

case class SimplePieResponse (status:String, sections: Iterable[SimplePieResponseEntry])

object SimplePieResponse extends ((String, Iterable[SimplePieResponseEntry])=>SimplePieResponse) {
  /**
    * generates a SimplePieResponse from the given aggregations
    * @param aggs
    * @param key
    * @param status
    * @return
    */
  def fromAggregations(aggs:Aggregations, key:String, emptyBucketAgg:Option[String], alternateCountField:Option[String], status:String) = {
    aggs.data.get(key) match {
      case None=>
        Left(s"No aggregations present for $key")
      case Some(data)=>
        try {
          val aggContent = data.asInstanceOf[Map[String, Any]]
          val buckets = aggContent("buckets").asInstanceOf[List[Map[String, Any]]]

          val entries = buckets.map(bucketContent => SimplePieResponseEntry(bucketContent("key").asInstanceOf[String],alternateCountField match {
            case None=>bucketContent("doc_count").asInstanceOf[Int]
            case Some(countField)=>bucketContent(countField).asInstanceOf[Map[String,Any]]("value").asInstanceOf[Int]
          }))

          emptyBucketAgg match {
            case None=>Right(new SimplePieResponse(status, entries))
            case Some(emptyBucketAggName)=>
              val emptyBucketData = aggs.data(emptyBucketAggName).asInstanceOf[Map[String, Any]]
              Right(new SimplePieResponse(status, entries :+ SimplePieResponseEntry(emptyBucketAgg.get, alternateCountField match {
                case None=>emptyBucketData("doc_count").asInstanceOf[Int]
                case Some(countField)=>emptyBucketData(countField).asInstanceOf[Map[String,Any]]("value").asInstanceOf[Int]
              })))
          }

        } catch {
          case ex: Throwable=>
            Left(ex.toString)
        }
    }
  }
}