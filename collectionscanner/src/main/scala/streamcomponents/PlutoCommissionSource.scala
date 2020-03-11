package streamcomponents

import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import akka.stream.stage.{AbstractOutHandler, GraphStage, GraphStageLogic}
import models.PlutoCommission
import org.slf4j.LoggerFactory
import vidispine.VSCommunicator
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.{Failure, Success}

class PlutoCommissionSource(recordsPerPage:Int=5)(implicit comm:VSCommunicator,mat:Materializer) extends GraphStage[SourceShape[PlutoCommission]] {
  private final val out:Outlet[PlutoCommission] = Outlet.create("PlutoCommissionSource")

  override def shape: SourceShape[PlutoCommission] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger:org.slf4j.Logger = LoggerFactory.getLogger(getClass)

    private var buffer:Seq[PlutoCommission] = Seq()
    private var currentRecord:Int = 1

    setHandler(out, new AbstractOutHandler {
      val onOutputHandler = createAsyncCallback[PlutoCommission](result=>push(out, result))
      val onCompleteHandler = createAsyncCallback[Unit](_=>completeStage())
      val onErrorHandler = createAsyncCallback[Throwable](err=>failStage(err))

      override def onDownstreamFinish(): Unit = {
        println("downstream finished, terminating")

      }

      override def onPull(): Unit = {
        val searchDoc = <CollectionSearchDocument version="2" xmlns="http://xml.vidispine.com/schema/vidispine">
          <field>
            <name>gnm_type</name>
            <value>Commission</value>
          </field>
        </CollectionSearchDocument>

        logger.debug("onPull")

        if(buffer.isEmpty) {
          logger.debug("buffer is empty, pulling more content...")
          val responseFut = comm.request(VSCommunicator.OperationType.PUT,
            s"/API/collection;number=$recordsPerPage;first=$currentRecord",
            Some(searchDoc.toString),
            Map("Content-Type"->"application/xml","Accept"->"application/xml"),
            Map("content"->"metadata")
          )

          responseFut.onComplete({
            case Failure(err)=>
              logger.error(s"VS request crashed: ", err)
              onErrorHandler.invoke(err)
            case Success(Left(vserr))=>
              logger.error(s"Vidispine returned a ${vserr.errorCode} error: ${vserr.message}")
              onErrorHandler.invoke(new RuntimeException(s"Vidispine returned a ${vserr.errorCode}"))
            case Success(Right(content))=>
              val parsedXml = scala.xml.XML.loadString(content)

              val maybeParsedCommissions = (parsedXml \ "collection").map(coll=>PlutoCommission.fromXml(coll))
              val failedConversions = maybeParsedCommissions.collect({case Failure(err)=>err})
              if(failedConversions.nonEmpty) {
                logger.error(s"${failedConversions.length} / ${maybeParsedCommissions.length} commissions failed to marshal")
                failedConversions.foreach(err=>logger.error("\t failure: ", err))
              }

              currentRecord+=recordsPerPage
              this.synchronized {
                buffer = buffer ++ maybeParsedCommissions.collect({case Success(comm)=>comm})
              }

              if(buffer.nonEmpty) {
                logger.debug(s"Got ${buffer.length} more items")
                val head = buffer.head
                this.synchronized {
                  buffer = buffer.tail
                }
                onOutputHandler.invoke(head)
              } else {
                logger.info("Processed all commissions")
                onCompleteHandler.invoke( () )
              }
          })
        } else {
          logger.debug(s"Pushing next commission, got ${buffer.length} remaining")
          val head = buffer.head
          this.synchronized {
            buffer = buffer.tail
          }
          push(out, head)
        }
      }
    })


  }
}
