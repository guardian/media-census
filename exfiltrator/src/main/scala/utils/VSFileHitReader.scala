package utils

import java.time.ZonedDateTime

import com.gu.vidispineakka.vidispine.{VSFile, VSFileItemMembership, VSFileShapeMembership, VSFileState}
import com.sksamuel.elastic4s.{Hit, HitReader}

import scala.util.Try

object VSFileHitReader {
  implicit object VSFileHitReader extends HitReader[VSFile] {
    /*
  for some strange reason "size" sometimes presents as an Integer and sometimes as a Long....
   */
    def fixNumberCast(someNumber: Any): Long = {
      try {
        someNumber.asInstanceOf[Integer].toLong
      } catch {
        case _: ClassCastException =>
          someNumber.asInstanceOf[Long]
      }
    }

    /*
  looks like the package confusion is preventing the auto-derivation from working :(
   */
    override def read(hit: Hit): Try[VSFile] = Try {
      val src = hit.sourceAsMap
      val maybeMembership = src.get("membership")
        .map(_.asInstanceOf[Map[String, Any]])
        .map(memsrc =>
          VSFileItemMembership(
            memsrc("itemId").asInstanceOf[String],
            memsrc("shapes")
              .asInstanceOf[Seq[Map[String, Any]]]
              .map(shapesrc => VSFileShapeMembership(
                shapesrc("shapeId").asInstanceOf[String],
                shapesrc("componentId").asInstanceOf[Seq[String]]
              ))
          )
        )

      VSFile(
        src("vsid").asInstanceOf[String],
        src("path").asInstanceOf[String],
        src("uri").asInstanceOf[String],
        src.get("state").map(_.asInstanceOf[String]).map(s => VSFileState.withName(s)),
        fixNumberCast(src("size")),
        src.get("hash").map(_.asInstanceOf[String]),
        ZonedDateTime.parse(src("timestamp").asInstanceOf[String]),
        src("refreshFlag").asInstanceOf[Int],
        src("storage").asInstanceOf[String],
        src.get("metadata").map(_.asInstanceOf[Map[String, String]]),
        maybeMembership,
        src.get("archiveHunterId").map(_.asInstanceOf[String]),
        src.get("archiveHunterConflict").map(_.asInstanceOf[Boolean])
      )
    }
  }

}