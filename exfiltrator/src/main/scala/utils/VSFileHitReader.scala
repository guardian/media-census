package utils

import java.time.ZonedDateTime

import com.gu.vidispineakka.vidispine.{VSFile, VSFileItemMembership, VSFileShapeMembership, VSFileState}
import com.sksamuel.elastic4s.{Hit, HitReader}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object VSFileHitReader {
  private val logger = LoggerFactory.getLogger(getClass)
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

    /**
     * sometimes, the normal ".get" method is unsafe, as it returns Some(null).
     * we double-check the result here, to prevent that
     * @param from
     * @param key
     * @return
     */
    def safeGet(from:Map[String,AnyRef], key:String) = from.get(key).flatMap(Option.apply)

    /*
  looks like the package confusion is preventing the auto-derivation from working :(
   */
    override def read(hit: Hit): Try[VSFile] = Try {
      val src = hit.sourceAsMap

      val maybeMembership = Try {
        safeGet(src, "membership")
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
        }

      maybeMembership match {
        case Failure(err) =>
          logger.error(s"Membership info from ${Option(src("vsid")).map(_.asInstanceOf[String])} was invalid: ${src.get("membership")}")
          throw err
        case Success(actualMembership) =>
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
            actualMembership,
            src.get("archiveHunterId").map(_.asInstanceOf[String]),
            src.get("archiveHunterConflict").map(_.asInstanceOf[Boolean])
          )
      }
    }
  }

}