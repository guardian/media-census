package vidispineclient

import akka.actor.ActorSystem
import akka.stream.Materializer
import config.VSConfig
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSStorage}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.xml.XML

class VSPathMapper(config:VSConfig)(implicit val materializer:Materializer, actorSystem:ActorSystem) {
  val logger = LoggerFactory.getLogger(getClass)

  val communicator = new VSCommunicator(config.vsUri, config.plutoUser, config.plutoPass)

  def getStorageList(xmlString:String):Try[Seq[VSStorage]] = {
    val xmlNodes = XML.loadString(xmlString)
    val seqMaybeNodes = (xmlNodes \ "storage")
      .map(storageNode=>VSStorage.fromXml(storageNode))

    logger.debug("Got storage data:")
    seqMaybeNodes.foreach(maybeNode=>logger.debug(maybeNode.toString))

    val failures = seqMaybeNodes.collect({case Failure(err)=>err})
    if(failures.nonEmpty){
      Failure(failures.head)
    } else {
      Success(seqMaybeNodes.collect({case Success(storageList)=>storageList}))
    }
  }

  /**
    * retrieves a Map relating the filesystem path (as key) to the VSStorage object (as object)
    * @return a Future, containing either an error message or the String->VSStorage map
    */
  def getPathMap() = {
    communicator.requestGet("/API/storage",Map("Accept"->"application/xml")).map({
      case Left(err)=>Left(err.toString)
      case Right(xmlString)=>
        getStorageList(xmlString) match {
          case Failure(err)=>Left(err.toString)
          case Success(storageList)=>
            Right(storageList.flatMap(storagePtr=>storagePtr.fileSystemPaths.map(fsPath=>(s"/$fsPath", storagePtr))).toMap)
        }
    })
  }


}
