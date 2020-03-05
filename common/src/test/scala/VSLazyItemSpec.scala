import java.time.ZonedDateTime
import java.util.UUID

import akka.stream.{ActorMaterializer, Materializer}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import testhelpers.AkkaTestkitSpecs2Support
import vidispine.{VSCommunicator, VSLazyItem, VSMetadataEntry, VSMetadataValue}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.Source

class VSLazyItemSpec extends Specification with Mockito {
  def getTestData(filename:String) = {
    val s = Source.fromResource("testmeta.xml")
    val content = s.mkString
    s.close()
    content
  }

  "VSLazyItem.getMoreMetadata" should {
    "call out to VSCommunicator to request metadata for the given fields and return a fresh VSLazyItem with the metadata" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      val returnedXml = getTestData("testmeta.xml")

      val initialItem = VSLazyItem("VX-1234",Map())

      implicit val comm:VSCommunicator = mock[VSCommunicator]
      comm.request(any,any,any,any,any,any,any)(any,any) returns Future(Right(returnedXml))

      val result = Await.result(initialItem.getMoreMetadata(Seq("field1","field2","field3")), 30 seconds)

      result must beRight

      result.right.get.itemId mustEqual "VX-1234"
      result.right.get.lookedUpMetadata.isEmpty must beFalse
      result.right.get.lookedUpMetadata.get("__shape_size") must beSome(VSMetadataEntry("__shape_size",None,None,None,None,Seq(VSMetadataValue("2",None,None,None,None))))
      result.right.get.lookedUpMetadata.get("originalFilename") must beSome(
        VSMetadataEntry("originalFilename",Some(UUID.fromString("226ebee6-32b7-4299-8305-d289bbd48c89")),Some("system"),Some(ZonedDateTime.parse("2018-07-20T14:06:33.938+01:00")),Some("VX-193"),
          Seq(VSMetadataValue("getting busy.jpg",Some(UUID.fromString("d604533e-2083-4c0e-aa2f-763137b432ba")),Some("system"),Some(ZonedDateTime.parse("2018-07-20T14:06:33.938+01:00")),Some("VX-193")))))
    }
  }

  "VSLazyItem" should {
    "interpret repeated field names as multiple values" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      val returnedXml =
        """<?xml version="1.0"?>
          |<MetadataListDocument>
          | <item id="VX-12345">
          |   <metadata>
          |   <group>Asset</group>
          |   <timespan start="-INF" end="+INF">
          |     <field>
          |       <name>somefield</name>
          |       <value>somevalue</value>
          |     </field>
          |     <field>
          |       <name>someotherfield</name>
          |       <value>somevalue</value>
          |       <value>another value</value>
          |     </field>
          |     <field>
          |       <name>somerepeatedfield</name>
          |       <value>value one</value>
          |     </field>
          |     <field>
          |       <name>somerepeatedfield</name>
          |       <value>value two</value>
          |     </field>
          |   </timespan>
          |   </metadata>
          | </item>
          |</MetadataListDocument>
          |""".stripMargin

      val initialItem = VSLazyItem("VX-12345",Map())

      implicit val comm:VSCommunicator = mock[VSCommunicator]
      comm.request(any,any,any,any,any,any,any)(any,any) returns Future(Right(returnedXml))

      val result = Await.result(initialItem.getMoreMetadata(Seq("field1","field2","field3")), 30 seconds)

      result must beRight

      result.right.get.itemId mustEqual "VX-12345"
      result.right.get.lookedUpMetadata.isEmpty must beFalse

      val item = result.right.get
      item.get("dsfjhsf") must beNone
      item.get("somefield") must beSome(Seq("somevalue"))
      item.get("somerepeatedfield") must beSome(Seq("value one", "value two"))
      item.get("someotherfield") must beSome(Seq("somevalue","another value"))
    }
  }
}
