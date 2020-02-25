package models

import java.time.{LocalDate, ZonedDateTime}

import scala.util.Try
import scala.xml.{Elem, Node}

case class PlutoCommission (vsid:String, name:String, projects:List[String], status:String, scheduled_completion:Option[LocalDate])

object PlutoCommission {
  def extractMetadataValue(metaNode:Node, fieldName:String):Option[String] = {
    val applicableFieldNodes = (metaNode \ "timespan" \ "field").filter(fieldNode=>(fieldNode\"name").text==fieldName)
    applicableFieldNodes.headOption.map(fieldNode=>(fieldNode\"value").text)
  }

  def extractProjectList(metaNode:Node):List[String] = {
    val projectIdNodes = (metaNode \ "timespan" \ "field").filter(fieldNode=>(fieldNode\"name").text=="__child_collection")
    projectIdNodes.map(node=>(node\"value").text).toList
  }

  /*
<collection>
  <id>KP-54431</id>
  <name>March Agency Sport 2020</name>
  <metadata>
    <revision>KP-56720765,KP-56720771,KP-56725744,KP-56720769</revision>
    <timespan start="-INF" end="+INF">
      <field uuid="9c6bded4-3f81-4f1e-971a-ddb5a482b42b" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_scheduledcompletion</name>
        <value uuid="99d757b4-97f1-4e97-81ac-e0e7980f18ee" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">2021-03-01</value>
      </field>
      <field uuid="5c180da3-8a84-4c45-913a-bf08e2ad9c8d" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_type</name>
        <value uuid="afb12ffc-c0ee-4a4c-9a46-a6d6f40f95c2" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">Commission</value>
      </field>
      <field uuid="5ebd4e68-e07f-4e4c-9325-c918ef09f459" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_description</name>
        <value uuid="7cdd6bec-3bb4-491e-bf66-9f2ba772cb31" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771"/>
      </field>
      <field uuid="05bb2c84-39fd-4b5f-9174-e7c8977c23eb" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_subtype</name>
        <value uuid="2a83b135-97ff-46a2-b54f-14843955919c" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771"/>
      </field>
      <field uuid="e1913501-f036-4f62-9618-ac4af8b1da6a" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_licensor</name>
        <value uuid="69ab97d2-609a-40bc-86c5-f95d938ce267" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">Guardian News And Media</value>
      </field>
      <field uuid="f3fdf67c-b618-4d15-91d7-d07683937161" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_googledriveid</name>
        <value uuid="617a8239-778e-49c1-919f-a7df9ed6eba0" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">1c7e24X8G7eBSdjr8Y45hLJpKZPDt2DNK</value>
      </field>
      <field uuid="48a1af84-b57b-4a71-8bbc-8c6b7025b48b" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_workinggroup</name>
        <value uuid="6f13bd82-4854-4154-880f-c40ea4eeec94" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">41eb288b-dbaf-474e-9925-76635de901b1</value>
      </field>
      <field uuid="8954cda6-5107-4c71-8052-a001e6aa5d72" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_projecttype</name>
        <value uuid="89d2171f-86ef-4827-ab2b-41c19d6fa464" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">330b4d84-ef24-41e2-b093-0d15829afa64</value>
      </field>
      <field uuid="3d0dfcc7-fcb9-4b8d-91a3-79a1183e2e68" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_notes</name>
        <value uuid="d7c18395-b516-476d-b29c-74391626d8b1" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771"/>
      </field>
      <field uuid="62c88ea0-a739-440d-bad8-afade79b9886" user="yusuf_parkar" timestamp="2020-02-25T10:45:57.475Z" change="KP-56725744">
        <name>gnm_commission_status</name>
        <value uuid="94ef05cb-b992-4e26-9d59-62e2f8fc1fcc" user="yusuf_parkar" timestamp="2020-02-25T10:45:57.475Z" change="KP-56725744">In production</value>
      </field>
      <field uuid="4f11d87f-c0cc-4572-85c6-103298ed6420" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">
        <name>title</name>
        <value uuid="fb2972e1-4e43-4571-9aa0-2471cbfc880f" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">March Agency Sport 2020</value>
      </field>
      <field uuid="9764f7e9-8c25-4d83-bd1e-6d5f2e5d2dc7" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">
        <name>user</name>
        <value uuid="c3835fa4-c3d2-4755-bac9-58f5dcf591e4" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">nicholas_williams</value>
      </field>
      <field uuid="dfae51b3-45ad-473b-9d56-87d28c1339f0" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">
        <name>collectionId</name>
        <value uuid="3d5170bd-d867-4f9f-97bf-b81866bad520" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">KP-54431</value>
      </field>
      <field uuid="99d879f5-439e-4d4b-b7ba-983fb33d0436" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">
        <name>created</name>
        <value uuid="3fc6b5f7-6f20-4225-8255-a7432486b49b" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">2020-02-25T08:59:19.570Z</value>
      </field>
      <field uuid="5148e3f3-40f8-4398-ad41-cdc45ac26490" user="nicholas_williams" timestamp="2020-02-25T08:59:23.700Z" change="KP-56720769">
        <name>gnm_commission_spotify_series_id</name>
        <value uuid="0bf71774-dd6d-4c9c-ae70-dcb3ee9b2b78" user="nicholas_williams" timestamp="2020-02-25T08:59:23.700Z" change="KP-56720769">GSKP54431000000000000000</value>
      </field>
      <field uuid="d143db29-53fc-4a89-b78d-8bd0cd2063d2" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_googledriveurl</name>
        <value uuid="d9db4cfa-f6f4-4639-bf2b-7b69ee48813c" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">https://drive.google.com/drive/folders/1c7e24X8G7eBSdjr8Y45hLJpKZPDt2DNK</value>
      </field>
      <field uuid="7b388684-60f4-4818-95d8-b54b11c5349d" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_storage_rule_deep_archive</name>
        <value uuid="45b8893d-08bf-436c-ad9d-b9129f25c8ed" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">storage_rule_deep_archive</value>
      </field>
      <field uuid="98570f5d-b6f9-4c76-8da1-022d8f08a191" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_owner</name>
        <value uuid="3305ba80-b698-4b11-bde0-d1254a7ce08a" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">414</value>
      </field>
      <field uuid="e4c6325e-1885-4814-a954-bc24838f0632" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_client</name>
        <value uuid="64395ab9-8da1-447e-b04b-6bb67969bb6a" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">Guardian Editorial</value>
      </field>
      <field uuid="6ae453ba-cd02-41cc-bae2-26c2e430aede" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_intendeduploadplatforms</name>
        <value uuid="2f7fa3ca-7856-4a5a-a3ba-c95330244ac6" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">YouTube</value>
        <value uuid="8dd521d2-2ae9-4eb6-acc6-96b07bd92e74" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">Website</value>
      </field>
      <field uuid="bfc161da-083a-4bac-8a62-4f3d7d91e862" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_subscribers</name>
        <value uuid="5f181a50-50c6-4f9c-bbfe-5f13d5137390" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">414</value>
      </field>
      <field uuid="a3d90656-bedf-4fa3-a0fa-df7094fd6bb8" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_title</name>
        <value uuid="a5f54d17-0b42-48ed-925d-dcc676d2be81" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">March Agency Sport 2020</value>
      </field>
      <field uuid="0f07ec08-0f1c-4c2b-98f0-0d6eb452396b" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_commissioner</name>
        <value uuid="ee66da2c-9b42-40f1-84a5-fd9f0ad2078b" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">bd0db4b4-678e-4d17-a65a-e5f33922cf18</value>
      </field>
      <field uuid="f386eb00-33f5-4abb-99f2-073a7699ba05" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_created</name>
        <value uuid="195cc398-0f13-49b7-95b5-3a53bd0319a1" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771"/>
      </field>
      <field uuid="4d42cc86-6541-40f3-ae91-2e7c553c8d88" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_consumption_intent</name>
        <value uuid="d1d01fc1-1814-4a4b-b130-89ddbdc7fee5" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">sequential</value>
      </field>
      <field uuid="b1d5b9b9-262a-4780-a052-023cffa1d019" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_subscribing_groups</name>
        <value uuid="ac563203-d86d-4892-a8c2-3c2fb18fe896" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">51</value>
        <value uuid="fc31d4a7-fb78-462c-88de-6b255d9b8473" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">154</value>
      </field>
      <field uuid="3f40939d-be1b-41c9-a3cd-cedc2fb81e7b" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
        <name>gnm_commission_production_office</name>
        <value uuid="7e232c88-1d79-48b4-b574-6b17649c2506" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">UK</value>
      </field>
      <field>
        <name>__metadata_last_modified</name>
        <value>2020-02-25T10:45:57.475Z</value>
      </field>
      <field>
        <name>__parent_collection_size</name>
        <value>0</value>
      </field>
      <field>
        <name>__child_collection_size</name>
        <value>0</value>
      </field>
      <field>
        <name>__items_size</name>
        <value>0</value>
      </field>
      <field>
        <name>__ancestor_collection_size</name>
        <value>0</value>
      </field>
      <field>
        <name>__folder_mapped</name>
        <value>false</value>
      </field>
    </timespan>
  </metadata>
</collection>
   */
  def fromXml(collection:Node):Try[PlutoCommission] = Try {
    PlutoCommission(
      (collection \ "id").text,
      (collection \ "name").text,
      extractProjectList((collection \ "metadata").head),
      extractMetadataValue((collection \ "metadata").head, "gnm_commission_status").getOrElse("unknown"),
      extractMetadataValue((collection \ "metadata").head, "gnm_commission_scheduledcompletion").map(LocalDate.parse)
    )
  }
}
