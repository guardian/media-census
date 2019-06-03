package helpers

import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties, HttpClient}
import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class ESClientManager @Inject() (config:Configuration) {
  var cachedClient:Option[ElasticClient] = None

  def getClient() = {
    val uri = config.getOptional[String]("elasticsearch.uri") match {
      case Some(uri)=>uri
      case None=>s"http://${config.get[String]("elasticsearch.host")}:${config.getOptional[Int]("elasticsearch.port").getOrElse(9200)}"
    }
    ElasticClient(ElasticProperties(uri))
  }

  def getCachedClient() = {
    cachedClient match {
      case None=>
        cachedClient = Some(getClient())
        cachedClient.get
      case Some(client)=>client
    }
  }
}
