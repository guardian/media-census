package responses

import com.sksamuel.elastic4s.http.search.Aggregations

case class SimplePieResponseEntry(label: String, value: Double)

case class SimplePieResponse (status:String, sections: Iterable[SimplePieResponseEntry])

object SimplePieResponse extends ((String, Iterable[SimplePieResponseEntry])=>SimplePieResponse) {
  /**
    * generates a SimplePieResponse from the given aggregations
    * @param aggs
    * @param key
    * @param status
    * @return
    */
  def fromAggregations(aggs:Aggregations, key:String, status:String) = {
    aggs.data.get(key) match {
      case None=>
        Left(s"No aggregations present for $key")
      case Some(data)=>
        try {
          val aggContent = data.asInstanceOf[Map[String, Any]]
          val buckets = aggContent("buckets").asInstanceOf[Map[String, Any]]

          val entries = buckets.map(entry => SimplePieResponseEntry(entry("key"), entry("doc_count")))

          Right(new SimplePieResponse(status, entries))
        } catch {
          case ex: Throwable=>
            Left(ex.toString)
        }
    }
  }
}