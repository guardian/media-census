import java.time.{ZoneId, ZonedDateTime}

import helpers.ZonedDateTimeEncoder
import org.specs2.mutable.Specification
import vidispine.{VSFile, VSFileItemMembership, VSFileShapeMembership, VSFileState, VSFileStateEncoder}

class VSFileSpec extends Specification with VSFileStateEncoder with ZonedDateTimeEncoder {
  "VSFile.storageSubpath" should {
    "strip the storage path out of a fullpath" in {
      val result = VSFile.storageSubpath("/path/to/storage/with/media/on/it","/path/to/storage")
      result must beSome("with/media/on/it")
    }

    "test what happens if they don't overlap" in {
      val result = VSFile.storageSubpath("/path/to/storage/with/media/on/it","/completely/different/path")
      result must beNone
    }
  }

  "VSFile.fromXml" should {
    "not fail if there is an empty string for file state" in {
      val sampleFileXml = """<file>
                            |            <id>VX-23948</id>
                            |            <path>EUBrusselsBrexitDebateRecording.mp4</path>
                            |            <uri>file:///srv/Proxies2/DevSystem/DAM/Scratch/EUBrusselsBrexitDebateRecording.mp4</uri>
                            |            <state></state>
                            |            <size>490896691</size>
                            |            <hash>af42e9eb15d4643238e990e7714492fc2fa0f8a7</hash>
                            |            <timestamp>2019-05-21T12:16:34.970+01:00</timestamp>
                            |            <refreshFlag>1</refreshFlag>
                            |            <storage>VX-18</storage>
                            |            <metadata>
                            |                <field>
                            |                    <key>created</key>
                            |                    <value>1558437337823</value>
                            |                </field>
                            |                <field>
                            |                    <key>mtime</key>
                            |                    <value>1558437337823</value>
                            |                </field>
                            |            </metadata>
                            |        </file>"""
      val result = VSFile.fromXmlString(sampleFileXml)
      result must beSuccessfulTry(Some(VSFile("VX-23948",
      "EUBrusselsBrexitDebateRecording.mp4",
      "file:///srv/Proxies2/DevSystem/DAM/Scratch/EUBrusselsBrexitDebateRecording.mp4",
        None,
        490896691L,
        Some("af42e9eb15d4643238e990e7714492fc2fa0f8a7"),
        ZonedDateTime.parse("2019-05-21T12:16:34.970+01:00"),
        1,
        "VX-18",
        Some(Map("created"->"1558437337823","mtime"->"1558437337823")),
        None,
        None,
        None
      )))
    }

    "not fail if an empty node is passed" in {
      val sampleFileXml = """<FileListDocument xmlns="http://xml.vidispine.com/schema/vidispine"/>"""
      val result = VSFile.fromXmlString(sampleFileXml)

      result must beSuccessfulTry(None)
    }
  }

  "VSFile.partialMap" should {
    "output a map that only contains the right fields" in {
      val t = ZonedDateTime.now()
      val toTest = VSFile("VX-1234","/path/to/file","file://path/to/file",Some(VSFileState.CLOSED),1234L,Some("hash-goes-here"),t,1,"VX-2",None,None,None)

      val result = toTest.partialMap()
      result.contains("archiveHunterId") mustEqual false
      result.contains("membership") mustEqual false
      result.get("vsid") must beSome("VX-1234")
      result.get("path") must beSome("/path/to/file")
      result.get("uri") must beSome("file://path/to/file")
      result.get("size") must beSome(1234L)
    }
  }

  "VSFile" should {
    "encode correctly to JSON" in {
      import io.circe.generic.auto._
      import io.circe.syntax._

      val t = ZonedDateTime.of(2020,1,2,3,4,5,0,ZoneId.systemDefault())
      val membership = VSFileItemMembership("VX-5678",Seq(VSFileShapeMembership("VX-910",Seq("VX-111"))))
      val toTest = VSFile("VX-1234","/path/to/file","file://path/to/file",Some(VSFileState.CLOSED),1234L,Some("hash-goes-here"),t,0,"VX-2",None,Some(membership),None)

      val result = toTest.asJson.toString()
      println(s"got $result")
      result mustEqual """{
                         |  "vsid" : "VX-1234",
                         |  "path" : "/path/to/file",
                         |  "uri" : "file://path/to/file",
                         |  "state" : "CLOSED",
                         |  "size" : 1234,
                         |  "hash" : "hash-goes-here",
                         |  "timestamp" : "2020-01-02T03:04:05Z",
                         |  "refreshFlag" : 0,
                         |  "storage" : "VX-2",
                         |  "metadata" : null,
                         |  "membership" : {
                         |    "itemId" : "VX-5678",
                         |    "shapes" : [
                         |      {
                         |        "shapeId" : "VX-910",
                         |        "componentId" : [
                         |          "VX-111"
                         |        ]
                         |      }
                         |    ]
                         |  },
                         |  "archiveHunterId" : null,
                         |  "archiveConflict" : null
                         |}""".stripMargin
    }

    "decode correclty from json" in {
      import io.circe.generic.auto._
      import io.circe.syntax._
      val rawjson = """{
                      |  "refreshFlag": 1,
                      |  "membership": {
                      |    "itemId": "KP-619187",
                      |    "shapes": [
                      |      {
                      |        "shapeId": "KP-1195358",
                      |        "componentId": [
                      |          "KP-2988899",
                      |          "KP-2988897",
                      |          "KP-2988898",
                      |          "KP-2988895",
                      |          "KP-2988896",
                      |          "KP-2988894"
                      |        ]
                      |      }
                      |    ]
                      |  },
                      |  "path": "151127Ambulance_1_KP-26082010_KP-619187.mxf",
                      |  "timestamp": "2015-11-30T01:07:02.460Z",
                      |  "size": -1,
                      |  "state": "LOST",
                      |  "uri": "",
                      |  "vsid": "KP-26082057",
                      |  "hash": "",
                      |  "metadata": {
                      |    "created": "1448637476000",
                      |    "mtime": "1448637476000"
                      |  },
                      |  "storage": "KP-2"
                      |}""".stripMargin
      val result = io.circe.parser.parse(rawjson).flatMap(_.as[VSFile])
      result must beRight

    }
  }
}
