package models

import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor, Json}

import scala.util.Try

case class PlutoProject (siteIdentifier:Option[String], collectionId:Int, name:Option[String], status:String, commissionCollection:Option[Int]) {
  def commissionVSID() = s"${siteIdentifier.getOrElse("VX")}-$commissionCollection"
  def projectVSID() = s"${siteIdentifier.getOrElse("VX")}-$collectionId"
}


/*
  {
    "collection_id": 53555,
    "user": 80,
    "created": "2020-01-09T11:56:45.284",
    "updated": "2020-01-09T14:01:36.903",
    "commission": 50339,
    "gnm_project_status": "In Production",
    "gnm_project_standfirst": null,
    "gnm_project_headline": "Made in Britain: Project Template",
    "gnm_project_username": [
      80
    ],
    "gnm_project_project_file_item": null,
    "gnm_project_prelude_file_item": null,
    "gnm_project_type": "330b4d84-ef24-41e2-b093-0d15829afa64",
    "project_locker_id": 6053,
    "project_locker_id_prelude": null
  }
 */

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