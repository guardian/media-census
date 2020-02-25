import java.time.{ZoneId, ZonedDateTime}

import com.sksamuel.elastic4s.Hit
import models.VSFileHitReader
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import vidispine.{VSFile, VSFileItemMembership, VSFileShapeMembership, VSFileState}

/*
(vsid:String, path:String, uri:String, state:Option[VSFileState.Value], size:Long, hash:Option[String],
                  timestamp:ZonedDateTime,refreshFlag:Int,storage:String, metadata:Option[Map[String,String]],
                  membership: Option[VSFileItemMembership], archiveHunterId: Option[String], archiveConflict:Option[Boolean]=None)
 */

class VSFileHitReaderSpec extends Specification with Mockito {
  class ToTest extends VSFileHitReader

  /*
  "VSFileHitReader" should {
    "correctly decode a fully populated map" in {
      val mapData:Map[String,AnyRef] = Map(
        "vsid"->"VX-1234",
        "path"->"path/to/some/thing.mxf",
        "uri"->"file://path/to/some/thing.mxf",
        "state"->"CLOSED",
        "size"->1234567L,
        "hash"->"someHashHere",
        "timestamp"->"2019-01-02T03:04:05.678+0000",
        "refreshFlag"->1L,
        "storage"->"VX-3",
        "metadata"->Map("key"->"value"),
        "membership"->Map("itemId"->"VX-111", "shapes"->Seq(Map("shapeId"->"VX-234", "componentId"->Seq("Container")))),
        "archiveHunterId"->"something",
        "archiveConflict"->0L
      )
      val fakeHit = mock[Hit]
      fakeHit.sourceAsMap returns mapData

      val t = new ToTest
      val result = t.VSFileHitReader.read(fakeHit)

      result must beSuccessfulTry(VSFile(
        "VX-1234",
        "path/to/some/thing.mxf",
        "file://path/to/some/thing.mxf",
        Some(VSFileState.CLOSED),
        1234567L,
        Some("someHashHere"),
        ZonedDateTime.of(2019,1,2,3,4,5,678, ZoneId.of("UTC")),
        1,
        "VX-3",
        Some(Map("key"->"value")),
        Some(VSFileItemMembership("VX-111",Seq(VSFileShapeMembership("VX-234",Seq("Container"))))),
        Some("something"),
        Some(false)
      ))
    }
  }*/
}
