package models

import org.specs2.mutable.Specification

class PlutoProjectSpec extends Specification {
  import io.circe.syntax._
  import PlutoProject._

  "PlutoProject" should {
    "unmarshal from array format" in {
      val rawJson = """  {
                      |    "collection_id": 53555,
                      |    "user": 80,
                      |    "created": "2020-01-09T11:56:45.284",
                      |    "updated": "2020-01-09T14:01:36.903",
                      |    "commission": 50339,
                      |    "gnm_project_status": "In Production",
                      |    "gnm_project_standfirst": null,
                      |    "gnm_project_headline": "Made in Britain: Project Template",
                      |    "gnm_project_username": [
                      |      80
                      |    ],
                      |    "gnm_project_project_file_item": null,
                      |    "gnm_project_prelude_file_item": null,
                      |    "gnm_project_type": "330b4d84-ef24-41e2-b093-0d15829afa64",
                      |    "project_locker_id": 6053,
                      |    "project_locker_id_prelude": null
                      |  }""".stripMargin
      val a = io.circe.parser.parse(rawJson).flatMap(_.as[PlutoProject])
      a
      a must beRight(PlutoProject(None,53555,"Made in Britain: Project Template", "In Production", Some(50339)))
    }
  }
}
