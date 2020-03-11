package models

import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor, Json}
import scala.util.Try

case class PlutoProject (siteIdentifier:Option[String], collectionId:Int, name:Option[String], status:String, commissionCollection:Option[Int]) {
  def commissionVSID() = s"${siteIdentifier.getOrElse("VX")}-$commissionCollection"
  def projectVSID() = s"${siteIdentifier.getOrElse("VX")}-$collectionId"
}

object PlutoProject {
  import io.circe.syntax._

  implicit val decodePlutoProject: Decoder[PlutoProject] = new Decoder[PlutoProject] {
    override final def apply(c: HCursor): Decoder.Result[PlutoProject] =
      for {
        collectionId <- c.downField("collection_id").as[Int]
        name <- c.downField("gnm_project_headline").as[Option[String]]
        status <- c.downField("gnm_project_status").as[String]
        commissionCollection <- c.downField("commission").as[Option[Int]]
      } yield PlutoProject(None,collectionId, name, status, commissionCollection)
  }

  def fromJson(json:Json) = json.as[PlutoProject]
}