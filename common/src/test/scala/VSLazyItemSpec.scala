import java.time.ZonedDateTime
import java.util.UUID

import akka.stream.{ActorMaterializer, Materializer}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import vidispine.{VSCommunicator, VSLazyItem, VSMetadataEntry, VSMetadataValue}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class VSLazyItemSpec extends Specification with Mockito {
  "VSLazyItem.getMoreMetadata" should {
    "call out to VSCommunicator to request metadata for the given fields and return a fresh VSLazyItem with the metadata" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      val returnedXml = """"""
      val initialItem = VSLazyItem("VX-1234",Map())

      implicit val comm:VSCommunicator = mock[VSCommunicator]
      comm.requestGet(anyString,any,any,any)(any,any) returns Future(Right(returnedXml))

      val result = Await.result(initialItem.getMoreMetadata(Seq("field1","field2","field3")), 30 seconds)

      result must beRight(VSLazyItem("VX-1234",
        Map("something"->VSMetadataEntry("something",UUID.randomUUID(),"system",ZonedDateTime.now(),"",Seq(VSMetadataValue("somevalue",UUID.randomUUID(),"system",ZonedDateTime.now(),""))))))
    }
  }
}
