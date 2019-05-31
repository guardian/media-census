package helpers

import com.sksamuel.elastic4s.bulk.BulkCompatibleDefinition
import com.sksamuel.elastic4s.streams.RequestBuilder
import models.MediaCensusEntry
import io.circe.generic.auto._

/**
  * the generator for bulk indexing requests for streaming interface
  */
trait CensusEntryRequestBuilder extends ZonedDateTimeEncoder {
  val indexName:String

  implicit val builder = new RequestBuilder[MediaCensusEntry] {
    import com.sksamuel.elastic4s.http.ElasticDsl._
    import com.sksamuel.elastic4s.circe._

    override def request(entry: MediaCensusEntry): BulkCompatibleDefinition = update(entry.originalSource.id.toString).in(s"$indexName/censusentry").docAsUpsert(entry)
  }
}
