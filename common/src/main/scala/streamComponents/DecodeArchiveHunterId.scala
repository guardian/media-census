package streamComponents

import java.util.Base64

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import vidispine.VSFile

class DecodeArchiveHunterId extends GraphStage[FlowShape[VSFile,(VSFile, String, String)]] {
  private final val in:Inlet[VSFile] = Inlet.create("DecodeArchiveHunterId.in")
  private final val out:Outlet[(VSFile,String,String)] = Outlet.create("DecodeArchiveHunterId.out")

  override def shape: FlowShape[VSFile, (VSFile, String, String)] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private val decoder = Base64.getDecoder

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        elem.archiveHunterId match {
          case None=>
            logger.error(s"The incoming item ${elem.uri} has no archivehunter id, this probably indicates a bug")
            pull(in)
          case Some(archiveHunterId)=>
            val decodedId = new String(decoder.decode(archiveHunterId))
            logger.debug(s"decoded ID is $decodedId")
            val parts = decodedId.split(":")
            if(parts.length<2){
              logger.error(s"The decoded ID for ${elem.uri} ($decodedId) does not seem properly formed, not enough :")
              pull(in)
            } else {
              val collectionName = parts.head
              val path = parts.tail.mkString(":")
              push(out, (elem, collectionName, path))
            }
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
