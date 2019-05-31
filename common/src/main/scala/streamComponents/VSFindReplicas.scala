package streamComponents

import akka.stream.{Attributes, Inlet, Materializer, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.{MediaCensusEntry, VSFileLocation}
import org.slf4j.LoggerFactory
import vidispine.{VSCommunicator, VSFile, VSShape}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

class VSFindReplicas (implicit vsCommunicator:VSCommunicator, mat:Materializer) extends GraphStage[UniformFanOutShape[MediaCensusEntry, MediaCensusEntry]] {
  private final val in:Inlet[MediaCensusEntry] = Inlet.create("VSFileSwitch.in")
  private final val outYes:Outlet[MediaCensusEntry] = Outlet.create("VSFileSwitch.yes")
  private final val outNo:Outlet[MediaCensusEntry] = Outlet.create("VSFileSwitch.no")

  override def shape = new UniformFanOutShape[MediaCensusEntry, MediaCensusEntry](in,Array(outYes, outNo))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val logger = LoggerFactory.getLogger(getClass)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val completionCb = getAsyncCallback[MediaCensusEntry](finalElem=>{
          if(finalElem.replicas.nonEmpty){
            push(outYes, finalElem)
          } else {
            push(outNo, finalElem)
          }
        })

        if(elem.vsItemId.isEmpty || elem.vsShapeIds.isEmpty) {
          logger.warn(s"${elem.storageSubpath} is not attached to items and/or shapes")
          push(outNo, elem)
        } else {
          val shapeListFuture = Future.sequence(elem.vsShapeIds.get.map(shapeId=>VSShape.forItemWithId(elem.vsItemId.get, shapeId)))

          shapeListFuture.map(results=>{
            logger.info(s"Got $results for shape list enquiry")
            val failures = results.collect({case Left(err)=>err})
            if(failures.nonEmpty){
              logger.error(s"Could not look up shapes, ${failures.length} out of ${results.length} requests errored:")
              failures.foreach(err=>logger.error(err.toString))
              failStage(new RuntimeException(s"Communication failure, see logs for details"))
            }

            /**
              * this code works fine for the "nothing found" case .
              * in that case, completionCb detects that there is nothing in the replica list
              * and pushes to the "no" channel
              */
            val shapes = results.collect({case Right(shape)=>shape})
            val replicaList = shapes.flatMap(_.files)
            logger.info(s"Found replicas list $replicaList for ${elem.storageSubpath}")
            val updatedElem = elem.copy(
              replicas = replicaList.map(VSFileLocation.fromVsFile)
            )
            completionCb.invoke(updatedElem)
          })
        }
      }
    })

    setHandler(outYes, new AbstractOutHandler {
      override def onPull(): Unit = {
        if(!hasBeenPulled(in)) pull(in)
      }
    })

    setHandler(outNo, new AbstractOutHandler {
      override def onPull(): Unit = {
        if(!hasBeenPulled(in)) pull(in)
      }
    })
  }
}

