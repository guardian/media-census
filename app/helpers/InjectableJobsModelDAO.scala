package helpers

import javax.inject.{Inject, Singleton}
import models.JobHistoryDAO
import play.api.Configuration

@Singleton
class InjectableJobsModelDAO @Inject() (config:Configuration, esClientManager: ESClientManager){
  val esClient = esClientManager.getCachedClient()
  val jobsIndexName = config.get[String]("elasticsearch.jobsHistoryIndex")

  val dao = new JobHistoryDAO(esClient, jobsIndexName)
}
