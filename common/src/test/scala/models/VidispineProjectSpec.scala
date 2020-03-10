package models

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.specs2.mutable.Specification

class VidispineProjectSpec extends Specification {
  val sampleXml = """<collection>
                    |<id>KP-18732</id>
                    |<name>Politics Weekly Monbiot Miliband</name>
                    |<metadata>
                    |<revision>KP-57312236,KP-57312242,KP-57312241</revision>
                    |<timespan start="-INF" end="+INF">
                    |<field uuid="f74c3cfd-b5cb-4fbd-bf2b-4b30a23f4f8c" user="system" timestamp="2016-03-29T16:39:47.318+01:00" change="KP-8721240">
                    |<name>created</name>
                    |<value uuid="6a4e3718-c01d-4d41-9fdd-9aa13592c6a3" user="system" timestamp="2016-03-29T16:39:47.318+01:00" change="KP-8721240">2016-03-29T15:39:47.192Z</value>
                    | </field>
                    |<field uuid="387f5f94-fe40-4cb1-8684-2d3fda0f559f" user="system" timestamp="2016-03-29T16:39:47.318+01:00" change="KP-8721240">
                    |<name>user</name>
                    |<value uuid="ee16ea55-2ff5-4b0a-8822-1af90ec888c3" user="system" timestamp="2016-03-29T16:39:47.318+01:00" change="KP-8721240">phil_maynard</value>
                    | </field>
                    |<field uuid="2b282bd8-0b5a-4a1a-af47-305643cfe3a5" user="system" timestamp="2016-03-29T16:39:47.318+01:00" change="KP-8721240">
                    |<name>title</name>
                    |<value uuid="23a35e26-2465-4af3-96f1-4e36f8b21a25" user="system" timestamp="2016-03-29T16:39:47.318+01:00" change="KP-8721240">Politics Weekly Monbiot Miliband</value>
                    | </field>
                    |<field uuid="293e15e6-bccc-43dc-9b69-325b3daf0270" user="system" timestamp="2016-03-29T16:39:47.318+01:00" change="KP-8721240">
                    |<name>collectionId</name>
                    |<value uuid="ec161e04-52d1-4ac6-9056-83e971f10fce" user="system" timestamp="2016-03-29T16:39:47.318+01:00" change="KP-8721240">KP-18732</value>
                    | </field>
                    |<field uuid="f328bdf2-8db1-4077-8d0a-3182ff27c6a2" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_storage_rule_deep_archive</name>
                    |<value uuid="41ec369b-eb2f-4b67-a0b7-21986024bdcf" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">storage_rule_deep_archive</value>
                    | </field>
                    |<field uuid="45734cc7-900a-4b95-a0bf-272c6769952d" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_publish</name>
                    |<value uuid="ea85761f-f6dc-4552-bbfb-fa336e8283ac" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">2016-03-29</value>
                    | </field>
                    |<field uuid="f5c69dc4-f6f1-417d-9f63-fe3b8221838f" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_remove</name>
                    |<value uuid="e5a2d2de-4d66-4246-998d-7c13b5a45a70" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241" />
                    | </field>
                    |<field uuid="b8cfbfc1-e9d1-4251-a771-86b3bb5ba4fc" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_trail</name>
                    |<value uuid="e60a624a-54a6-48e0-aa8e-73d7d452f1e6" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">Politics Weekly 2016</value>
                    | </field>
                    |<field uuid="126930a7-e105-432c-a0e0-8fea3714237e" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_tags</name>
                    |<value uuid="fa3c403d-e7f2-4ea6-a610-c601d5f73b04" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241" />
                    | </field>
                    |<field uuid="8083aa48-1916-46b8-9cd2-016b1549f981" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_standfirst</name>
                    |<value uuid="acaa63e5-380f-49ae-9734-1362e094d230" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">Politics Weekly Monbiot Miliband Politics Weekly Monbiot Miliband Politics Weekly Monbiot Miliband Politics Weekly Monbiot MilibandPolitics Weekly Monbiot Miliband Politics Weekly Monbiot Miliband Politics Weekly Monbiot Miliband Politics Weekly Monbiot Miliband Politics Weekly Monbiot Miliband Politics Weekly Monbiot Miliband Politics Weekly Monbiot Miliband Politics Weekly Monbiot Miliband Politics Weekly Monbiot Miliband</value>
                    | </field>
                    |<field uuid="098dc602-02cc-4394-a942-0981275c4e13" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_subscribers</name>
                    |<value uuid="6fe1c7b5-b49c-47d1-a511-fde98d27ded3" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">17</value>
                    |<value uuid="3baf7175-c28c-4bf5-b9d3-c329e77ae4b6" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">34</value>
                    | </field>
                    |<field uuid="50cfb595-cdf9-4d61-95ab-1f3e8c7fd2e2" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_subscribing_groups</name>
                    |<value uuid="68468311-ba78-494a-bcd4-ea4c1af777d0" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">57</value>
                    | </field>
                    |<field uuid="10f86ffe-ddb7-4396-94c7-1ae22053df4c" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_username</name>
                    |<value uuid="c8235416-3b7b-4cec-8ae5-409f5624d2c4" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">17</value>
                    | </field>
                    |<field uuid="0c57e114-abe5-4856-ae92-15dd043daabb" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_headline</name>
                    |<value uuid="2d1031d2-38ad-4d9c-a208-3867761cf6e7" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">Politics Weekly Monbiot Miliband</value>
                    | </field>
                    |<field uuid="6327ffa8-9ee6-4a3d-931c-577162250f2e" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_type</name>
                    |<value uuid="c3548d18-552c-47a7-91e5-db655bca91e5" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">136a4278-0d29-455a-8f34-b7b5fc4cd1e1</value>
                    | </field>
                    |<field uuid="f8220631-5ef9-48b2-9627-569e5c9e543e" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_licensor</name>
                    |<value uuid="9cfd9b13-84bf-4ad7-b16a-d948b878ac1f" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">Guardian News And Media</value>
                    | </field>
                    |<field uuid="49600d7c-4cbf-4b42-b480-3263a7922279" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_subtype</name>
                    |<value uuid="2fcca92f-4bce-4828-97cd-a5e001b7c643" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">510697fa-6caf-4dcc-8924-5ff3a6a1b647</value>
                    | </field>
                    |<field uuid="3ac4a5c4-d005-4436-b008-e4be17bb73e3" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_type</name>
                    |<value uuid="5aaaabc9-491a-4557-9437-a8e4bd9384f8" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">Project</value>
                    | </field>
                    |<field uuid="87dc0e05-0458-4e7d-b31f-db60bbbdc791" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_linktext</name>
                    |<value uuid="379ab80a-55b5-43b2-99a2-6a9c882c6304" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">Politics Weekly 2016</value>
                    | </field>
                    |<field uuid="085aa1f0-1947-48fc-a052-7c89e059ecf4" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_project_byline</name>
                    |<value uuid="f10309a1-c407-47e0-abbf-bcbb21fc0e5b" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241" />
                    | </field>
                    |<field uuid="19bdfef1-03da-40b2-9295-6ef909b43071" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_commission_title</name>
                    |<referenced id="KP-17248" uuid="25f5e834-910c-4eba-802a-755d2e42602f" type="collection" />
                    |<value uuid="2aeb36f2-017e-4999-a65a-e73b43c324c5" user="audioproducer1" timestamp="2016-01-07T09:49:52.559Z" change="KP-7630602">Politics Weekly 2016</value>
                    | </field>
                    |<field uuid="e0c8ac0f-2c7e-49c0-ba32-55fcc11f1112" user="phil_maynard" timestamp="2016-03-29T16:39:48.877+01:00" change="KP-8721241">
                    |<name>gnm_commission_workinggroup</name>
                    |<referenced id="KP-17248" uuid="be7cc416-5844-4076-90cd-6fc1b82231c3" type="collection" />
                    |<value uuid="e56cd369-4239-4e70-ac59-1d88e78e3a23" user="audioproducer1" timestamp="2016-01-07T09:49:52.559Z" change="KP-7630602">72d71f7b-1d27-41d6-9c85-7f64aeceaf2d</value>
                    | </field>
                    |<field uuid="adbe7d9c-0562-4026-b071-39693dd23354" user="admin" timestamp="2017-09-07T08:55:53.373+01:00" change="KP-21216679">
                    |<name>gnm_project_status</name>
                    |<value uuid="13bcee1a-3069-4435-9309-11ddf4af14b3" user="admin" timestamp="2017-09-07T08:55:53.373+01:00" change="KP-21216679">Completed</value>
                    | </field>
                    |<field uuid="0eee3389-21ec-4a10-be78-cf27e06c98e5" user="phil_maynard" timestamp="2016-03-29T16:40:09.677+01:00" change="KP-8721251">
                    |<name>gnm_project_project_file_item</name>
                    |<value uuid="c5d7d855-5918-4c64-881f-9c021012e036" user="phil_maynard" timestamp="2016-03-29T16:40:09.677+01:00" change="KP-8721251">KP-1850332</value>
                    | </field>
                    |<field uuid="fed7a0d9-1534-4de9-ab4c-e602e3c54e03" user="admin" timestamp="2016-03-29T16:40:25.906+01:00" change="KP-8721253">
                    |<name>gnm_project_project_file_status</name>
                    |<value uuid="34512b9a-ec5e-44f8-ad0a-90b619f1a371" user="admin" timestamp="2016-03-29T16:40:25.906+01:00" change="KP-8721253">Creation Complete</value>
                    | </field>
                    |<field>
                    |<name>__metadata_last_modified</name>
                    |<value>2017-09-07T08:55:53.373+01:00</value>
                    | </field>
                    |<field>
                    |<name>__parent_collection_size</name>
                    |<value>1</value>
                    | </field>
                    |<field>
                    |<name>__parent_collection</name>
                    |<value>KP-17248</value>
                    | </field>
                    |<field>
                    |<name>__child_collection_size</name>
                    |<value>0</value>
                    | </field>
                    |<field>
                    |<name>__items_size</name>
                    |<value>17</value>
                    | </field>
                    |<field>
                    |<name>__ancestor_collection_size</name>
                    |<value>1</value>
                    | </field>
                    |<field>
                    |<name>__ancestor_collection</name>
                    |<value>KP-17248</value>
                    | </field>
                    |<field>
                    |<name>__folder_mapped</name>
                    |<value>false</value>
                    | </field>
                    | </timespan>
                    | </metadata>
                    | </collection>""".stripMargin

  "VidispineProject.fromXml" should {
    "return a successful domain object from xml data" in {
      val xml = scala.xml.XML.loadString(sampleXml)

      val response = VidispineProject.fromXml(xml)
      val pattern = DateTimeFormatter.ISO_OFFSET_DATE_TIME

      response must beSuccessfulTry(
        VidispineProject(
          "KP-18732",
          Some("Politics Weekly Monbiot Miliband"),
          Some("Completed"),
          Some("KP-17248"),
          true,
          false,
          false,
          Some(ZonedDateTime.parse("2016-03-29T15:39:47.192Z", pattern)),
          Some(ZonedDateTime.parse("2017-09-07T08:55:53.373+01:00", pattern))
        )
      )
    }
  }
}
