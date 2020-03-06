package models

import com.sksamuel.elastic4s.http.ElasticClient
import org.slf4j.LoggerFactory
import vidispine.{VSFile, VSLazyItem}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

//object MediaStatusValue extends Enumeration {
//  val SHOULD_BE_ARCHIVED,NO_PROJECT,BRANDING,DELETABLE,SENSITIVE,PROJECT_OPEN_COMMISSION_COMPLETED,PROJECT_OPEN_COMMISSION_EXPIRED,PROJECT_OPEN_COMMISSION_OPEN = Value
//}

case class UnclogStream (VSFile:VSFile, VSItem:Option[VSLazyItem], ParentProjects:Seq[VidispineProject], MediaStatus:Option[MediaStatusValue.Value]) {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * perform a lookup on the VSItem to populate parent projects
    * this is intended to be run from a mapAsync stage in an Akka stream
    * for example: ~> someflow.mapAsync(4)(_.lookupProjectsMapper) ~>
    * assuming that `someflow` is a component that outputs an `UnclogStream` object. this example will parallel out to
    * @param esClient elastic search client
    * @param indexer VidispineProjectIndexer object
    * @return a Future containing the updated UnclogStream object
    */
  def lookupProjectsMapper(esClient:ElasticClient, indexer:VidispineProjectIndexer) = VSItem match {
    case None=>
      Future(this)
    case Some(vsItem)=>
      vsItem.get("__collection") match {
        case None=>
          Future.failed(new RuntimeException("vsitem had no parent collection fields!"))
        case Some(parentCollectionNames)=>
          indexer.lookupBulk(esClient, parentCollectionNames).map({
            case Left(err)=>
              logger.error(s"Could not look up projects for $vsItem in the index: ${err.toString}")
              throw new RuntimeException("could not look up projects")
            case Right(projectSeq)=>
              this.copy(ParentProjects=projectSeq)
          })
      }
  }
}