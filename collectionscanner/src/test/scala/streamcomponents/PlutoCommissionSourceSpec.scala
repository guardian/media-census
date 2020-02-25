package streamcomponents

import java.time.LocalDate

import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import models.PlutoCommission
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import vidispine.VSCommunicator

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class PlutoCommissionSourceSpec extends Specification with Mockito{
  val sampleXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    |<CollectionListDocument xmlns="http://xml.vidispine.com/schema/vidispine">
                    |  <hits>1491</hits>
                    |  <collection>
                    |    <id>KP-54431</id>
                    |    <name>March Agency Sport 2020</name>
                    |    <metadata>
                    |      <revision>KP-56720765,KP-56720771,KP-56725744,KP-56720769</revision>
                    |      <timespan start="-INF" end="+INF">
                    |        <field uuid="9c6bded4-3f81-4f1e-971a-ddb5a482b42b" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_scheduledcompletion</name>
                    |          <value uuid="99d757b4-97f1-4e97-81ac-e0e7980f18ee" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">2021-03-01</value>
                    |        </field>
                    |        <field uuid="5c180da3-8a84-4c45-913a-bf08e2ad9c8d" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_type</name>
                    |          <value uuid="afb12ffc-c0ee-4a4c-9a46-a6d6f40f95c2" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">Commission</value>
                    |        </field>
                    |        <field uuid="5ebd4e68-e07f-4e4c-9325-c918ef09f459" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_description</name>
                    |          <value uuid="7cdd6bec-3bb4-491e-bf66-9f2ba772cb31" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771"/>
                    |        </field>
                    |        <field uuid="05bb2c84-39fd-4b5f-9174-e7c8977c23eb" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_subtype</name>
                    |          <value uuid="2a83b135-97ff-46a2-b54f-14843955919c" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771"/>
                    |        </field>
                    |        <field uuid="e1913501-f036-4f62-9618-ac4af8b1da6a" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_licensor</name>
                    |          <value uuid="69ab97d2-609a-40bc-86c5-f95d938ce267" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">Guardian News And Media</value>
                    |        </field>
                    |        <field uuid="f3fdf67c-b618-4d15-91d7-d07683937161" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_googledriveid</name>
                    |          <value uuid="617a8239-778e-49c1-919f-a7df9ed6eba0" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">1c7e24X8G7eBSdjr8Y45hLJpKZPDt2DNK</value>
                    |        </field>
                    |        <field uuid="48a1af84-b57b-4a71-8bbc-8c6b7025b48b" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_workinggroup</name>
                    |          <value uuid="6f13bd82-4854-4154-880f-c40ea4eeec94" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">41eb288b-dbaf-474e-9925-76635de901b1</value>
                    |        </field>
                    |        <field uuid="8954cda6-5107-4c71-8052-a001e6aa5d72" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_projecttype</name>
                    |          <value uuid="89d2171f-86ef-4827-ab2b-41c19d6fa464" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">330b4d84-ef24-41e2-b093-0d15829afa64</value>
                    |        </field>
                    |        <field uuid="3d0dfcc7-fcb9-4b8d-91a3-79a1183e2e68" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_notes</name>
                    |          <value uuid="d7c18395-b516-476d-b29c-74391626d8b1" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771"/>
                    |        </field>
                    |        <field uuid="62c88ea0-a739-440d-bad8-afade79b9886" user="yusuf_parkar" timestamp="2020-02-25T10:45:57.475Z" change="KP-56725744">
                    |          <name>gnm_commission_status</name>
                    |          <value uuid="94ef05cb-b992-4e26-9d59-62e2f8fc1fcc" user="yusuf_parkar" timestamp="2020-02-25T10:45:57.475Z" change="KP-56725744">In production</value>
                    |        </field>
                    |        <field uuid="4f11d87f-c0cc-4572-85c6-103298ed6420" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">
                    |          <name>title</name>
                    |          <value uuid="fb2972e1-4e43-4571-9aa0-2471cbfc880f" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">March Agency Sport 2020</value>
                    |        </field>
                    |        <field uuid="9764f7e9-8c25-4d83-bd1e-6d5f2e5d2dc7" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">
                    |          <name>user</name>
                    |          <value uuid="c3835fa4-c3d2-4755-bac9-58f5dcf591e4" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">nicholas_williams</value>
                    |        </field>
                    |        <field uuid="dfae51b3-45ad-473b-9d56-87d28c1339f0" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">
                    |          <name>collectionId</name>
                    |          <value uuid="3d5170bd-d867-4f9f-97bf-b81866bad520" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">KP-54431</value>
                    |        </field>
                    |        <field uuid="99d879f5-439e-4d4b-b7ba-983fb33d0436" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">
                    |          <name>created</name>
                    |          <value uuid="3fc6b5f7-6f20-4225-8255-a7432486b49b" user="system" timestamp="2020-02-25T08:59:19.658Z" change="KP-56720765">2020-02-25T08:59:19.570Z</value>
                    |        </field>
                    |        <field uuid="5148e3f3-40f8-4398-ad41-cdc45ac26490" user="nicholas_williams" timestamp="2020-02-25T08:59:23.700Z" change="KP-56720769">
                    |          <name>gnm_commission_spotify_series_id</name>
                    |          <value uuid="0bf71774-dd6d-4c9c-ae70-dcb3ee9b2b78" user="nicholas_williams" timestamp="2020-02-25T08:59:23.700Z" change="KP-56720769">GSKP54431000000000000000</value>
                    |        </field>
                    |        <field uuid="d143db29-53fc-4a89-b78d-8bd0cd2063d2" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_googledriveurl</name>
                    |          <value uuid="d9db4cfa-f6f4-4639-bf2b-7b69ee48813c" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">https://drive.google.com/drive/folders/1c7e24X8G7eBSdjr8Y45hLJpKZPDt2DNK</value>
                    |        </field>
                    |        <field uuid="7b388684-60f4-4818-95d8-b54b11c5349d" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_storage_rule_deep_archive</name>
                    |          <value uuid="45b8893d-08bf-436c-ad9d-b9129f25c8ed" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">storage_rule_deep_archive</value>
                    |        </field>
                    |        <field uuid="98570f5d-b6f9-4c76-8da1-022d8f08a191" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_owner</name>
                    |          <value uuid="3305ba80-b698-4b11-bde0-d1254a7ce08a" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">414</value>
                    |        </field>
                    |        <field uuid="e4c6325e-1885-4814-a954-bc24838f0632" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_client</name>
                    |          <value uuid="64395ab9-8da1-447e-b04b-6bb67969bb6a" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">Guardian Editorial</value>
                    |        </field>
                    |        <field uuid="6ae453ba-cd02-41cc-bae2-26c2e430aede" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_intendeduploadplatforms</name>
                    |          <value uuid="2f7fa3ca-7856-4a5a-a3ba-c95330244ac6" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">YouTube</value>
                    |          <value uuid="8dd521d2-2ae9-4eb6-acc6-96b07bd92e74" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">Website</value>
                    |        </field>
                    |        <field uuid="bfc161da-083a-4bac-8a62-4f3d7d91e862" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_subscribers</name>
                    |          <value uuid="5f181a50-50c6-4f9c-bbfe-5f13d5137390" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">414</value>
                    |        </field>
                    |        <field uuid="a3d90656-bedf-4fa3-a0fa-df7094fd6bb8" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_title</name>
                    |          <value uuid="a5f54d17-0b42-48ed-925d-dcc676d2be81" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">March Agency Sport 2020</value>
                    |        </field>
                    |        <field uuid="0f07ec08-0f1c-4c2b-98f0-0d6eb452396b" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_commissioner</name>
                    |          <value uuid="ee66da2c-9b42-40f1-84a5-fd9f0ad2078b" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">bd0db4b4-678e-4d17-a65a-e5f33922cf18</value>
                    |        </field>
                    |        <field uuid="f386eb00-33f5-4abb-99f2-073a7699ba05" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_created</name>
                    |          <value uuid="195cc398-0f13-49b7-95b5-3a53bd0319a1" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771"/>
                    |        </field>
                    |        <field uuid="4d42cc86-6541-40f3-ae91-2e7c553c8d88" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_consumption_intent</name>
                    |          <value uuid="d1d01fc1-1814-4a4b-b130-89ddbdc7fee5" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">sequential</value>
                    |        </field>
                    |        <field uuid="b1d5b9b9-262a-4780-a052-023cffa1d019" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_subscribing_groups</name>
                    |          <value uuid="ac563203-d86d-4892-a8c2-3c2fb18fe896" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">51</value>
                    |          <value uuid="fc31d4a7-fb78-462c-88de-6b255d9b8473" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">154</value>
                    |        </field>
                    |        <field uuid="3f40939d-be1b-41c9-a3cd-cedc2fb81e7b" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">
                    |          <name>gnm_commission_production_office</name>
                    |          <value uuid="7e232c88-1d79-48b4-b574-6b17649c2506" user="nicholas_williams" timestamp="2020-02-25T08:59:22.639Z" change="KP-56720771">UK</value>
                    |        </field>
                    |        <field>
                    |          <name>__metadata_last_modified</name>
                    |          <value>2020-02-25T10:45:57.475Z</value>
                    |        </field>
                    |        <field>
                    |          <name>__parent_collection_size</name>
                    |          <value>0</value>
                    |        </field>
                    |        <field>
                    |          <name>__child_collection_size</name>
                    |          <value>0</value>
                    |        </field>
                    |        <field>
                    |          <name>__items_size</name>
                    |          <value>0</value>
                    |        </field>
                    |        <field>
                    |          <name>__ancestor_collection_size</name>
                    |          <value>0</value>
                    |        </field>
                    |        <field>
                    |          <name>__folder_mapped</name>
                    |          <value>false</value>
                    |        </field>
                    |      </timespan>
                    |    </metadata>
                    |  </collection>
                    |  <collection>
                    |    <id>KP-54406</id>
                    |    <name>24 January 2020 Weekly News Agency</name>
                    |    <metadata>
                    |      <revision>KP-56656533,KP-56656534,KP-56656525,KP-56656524,KP-56667043</revision>
                    |      <timespan start="-INF" end="+INF">
                    |        <field uuid="e1525c95-4805-40ae-ae42-0397b6e4f950" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_licensor</name>
                    |          <value uuid="4a79428f-2761-4564-b763-d9ee750c4306" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">Guardian News And Media</value>
                    |        </field>
                    |        <field uuid="7b9b9950-0487-4051-8c9e-452d32c9ec0c" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_googledriveid</name>
                    |          <value uuid="93b9cb7a-1b37-4b93-a507-2995214748c8" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">1oWocKQA57ngmjzsS_ZpipblMeVKQkR_V</value>
                    |        </field>
                    |        <field uuid="5d743f60-d5c2-49e5-89f1-553ce640bd3a" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_workinggroup</name>
                    |          <value uuid="2821fda1-4753-46b0-b440-0987523b9954" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">1b97c363-fba0-4771-9cb5-9bd65aaed306</value>
                    |        </field>
                    |        <field uuid="f2e53c00-8ebc-439d-922a-1399de52f2aa" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_projecttype</name>
                    |          <value uuid="06e34ad3-d509-4a54-aeb5-770b2de729b9" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">330b4d84-ef24-41e2-b093-0d15829afa64</value>
                    |        </field>
                    |        <field uuid="9bd80cf3-2a93-41cb-abac-533677b8c443" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_scheduledcompletion</name>
                    |          <value uuid="d42a1aa4-eab7-4d54-ae98-a7a25060d639" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">2020-03-01</value>
                    |        </field>
                    |        <field uuid="2d2f8d3c-e7a5-4204-8ec9-d97b9e34a6f5" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_type</name>
                    |          <value uuid="d5fa6d61-4bc9-4078-98bd-d8c865f07b3a" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">Commission</value>
                    |        </field>
                    |        <field uuid="8ccf3b33-c1a8-4830-8940-6758461908e5" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_description</name>
                    |          <value uuid="4e32aa42-5a68-43bb-87fc-d010d30b2d65" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524"/>
                    |        </field>
                    |        <field uuid="91dff246-c62c-4a62-ac45-e44732222574" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_subtype</name>
                    |          <value uuid="6795b980-49f5-45ad-b852-b0c1b24f36c6" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524"/>
                    |        </field>
                    |        <field uuid="3be77cee-d683-4276-81d3-2e2aa2f943bf" user="Amy_OBrien" timestamp="2020-02-24T08:32:56.698Z" change="KP-56656525">
                    |          <name>gnm_commission_spotify_series_id</name>
                    |          <value uuid="d118993f-7e8f-4a95-9c08-f4c7ac095125" user="Amy_OBrien" timestamp="2020-02-24T08:32:56.698Z" change="KP-56656525">GSKP54406000000000000000</value>
                    |        </field>
                    |        <field uuid="f84dfbce-cb1e-46f8-a7a9-cfcc39d42471" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_notes</name>
                    |          <value uuid="db343a4d-776a-4c02-be8c-bf6d05355bbd" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524"/>
                    |        </field>
                    |        <field uuid="46d66f02-7552-419b-a394-49838e4dec43" user="yusuf_parkar" timestamp="2020-02-24T16:11:39.192Z" change="KP-56667043">
                    |          <name>gnm_commission_status</name>
                    |          <value uuid="327b1c90-d9f2-4357-9e11-c1a819a5b74f" user="yusuf_parkar" timestamp="2020-02-24T16:11:39.192Z" change="KP-56667043">In production</value>
                    |        </field>
                    |        <field uuid="70a91135-1b44-44e8-98de-b38e2faabc5f" user="system" timestamp="2020-02-24T08:32:53.129Z" change="KP-56656533">
                    |          <name>created</name>
                    |          <value uuid="8ccdbc05-11c0-41bd-8f34-0097e5f99bff" user="system" timestamp="2020-02-24T08:32:53.129Z" change="KP-56656533">2020-02-24T08:32:52.970Z</value>
                    |        </field>
                    |        <field uuid="3583fbf3-5af6-4235-890d-2341eb1feab5" user="system" timestamp="2020-02-24T08:32:53.129Z" change="KP-56656533">
                    |          <name>user</name>
                    |          <value uuid="73940ddc-b9bc-4ae6-9bd2-4437d2247cff" user="system" timestamp="2020-02-24T08:32:53.129Z" change="KP-56656533">Amy_OBrien</value>
                    |        </field>
                    |        <field uuid="6f767151-1629-4871-a2dd-f6e618ead6a6" user="system" timestamp="2020-02-24T08:32:53.129Z" change="KP-56656533">
                    |          <name>title</name>
                    |          <value uuid="5aebab69-2e98-4bd9-8d38-83f6374bfaed" user="system" timestamp="2020-02-24T08:32:53.129Z" change="KP-56656533">24 January 2020 Weekly News Agency</value>
                    |        </field>
                    |        <field uuid="eefc0756-a926-4a86-b4c7-c6426b0e5d9b" user="system" timestamp="2020-02-24T08:32:53.129Z" change="KP-56656533">
                    |          <name>collectionId</name>
                    |          <value uuid="07e3a7dd-ade5-4fed-85a2-79b1ef95cea0" user="system" timestamp="2020-02-24T08:32:53.129Z" change="KP-56656533">KP-54406</value>
                    |        </field>
                    |        <field uuid="4f85b84f-c8ca-461f-90e2-2f3870b985ef" user="Amy_OBrien" timestamp="2020-02-24T08:33:27.423Z" change="KP-56656534">
                    |          <name>gnm_commission_title</name>
                    |          <value uuid="deaaafc3-80ae-447a-99ed-936ad3d94559" user="Amy_OBrien" timestamp="2020-02-24T08:33:27.423Z" change="KP-56656534">24 February 2020 Weekly News Agency</value>
                    |        </field>
                    |        <field uuid="78a133d6-6324-42c2-9146-cd8bac5c8068" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_googledriveurl</name>
                    |          <value uuid="d7ba5fbb-d447-448d-a9f1-31c79f3c9c82" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">https://drive.google.com/drive/folders/1oWocKQA57ngmjzsS_ZpipblMeVKQkR_V</value>
                    |        </field>
                    |        <field uuid="ef40a496-9e14-4d3e-90e8-984023c15822" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_subscribers</name>
                    |          <value uuid="aaf2d7b6-54b8-4981-9d6d-5eede685d1ed" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">660</value>
                    |        </field>
                    |        <field uuid="e4690ad1-9f8a-401e-aae9-a9078c1df00d" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_client</name>
                    |          <value uuid="aa5146be-1f97-4995-a5cb-8d32abfcd373" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">Guardian Editorial</value>
                    |        </field>
                    |        <field uuid="715f091f-63c5-44c0-bc93-dacfd2dba2e8" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_storage_rule_deep_archive</name>
                    |          <value uuid="7e159dbd-64f8-43db-a9ff-731e2e13673f" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">storage_rule_deep_archive</value>
                    |        </field>
                    |        <field uuid="33def432-4732-4c5e-bb75-a251c52a5036" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_consumption_intent</name>
                    |          <value uuid="13b84633-705a-494e-a540-07a3d236e409" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">sequential</value>
                    |        </field>
                    |        <field uuid="943ad76b-017c-468b-a6c7-dd3fe592144f" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_owner</name>
                    |          <value uuid="0b8f512c-1e3f-4bd3-9ab9-eff63a8eefd5" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">660</value>
                    |        </field>
                    |        <field uuid="4af460d2-af04-4902-8bdc-425148d4e168" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_production_office</name>
                    |          <value uuid="b158195a-225f-451c-856c-eff8064a74d8" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">UK</value>
                    |        </field>
                    |        <field uuid="9b689d35-d79a-4753-b9f8-7ae63d42bb9b" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_created</name>
                    |          <value uuid="3c557a75-5372-4041-ac31-cb75d826e6ca" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524"/>
                    |        </field>
                    |        <field uuid="6b80198b-aa35-4482-8ed4-2969be9cb420" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_subscribing_groups</name>
                    |          <value uuid="79ccca18-0f50-4693-8f78-4d2ffc30f07a" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">51</value>
                    |          <value uuid="51b12167-a44b-420d-94f0-5bbe91e58ef3" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">154</value>
                    |        </field>
                    |        <field uuid="670f33a4-57e4-4931-9497-cff3aa0aa2e7" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">
                    |          <name>gnm_commission_commissioner</name>
                    |          <value uuid="181ab1f6-de12-44f6-8191-76c557d59058" user="Amy_OBrien" timestamp="2020-02-24T08:32:55.592Z" change="KP-56656524">b386f8c4-b402-4989-a0a6-91edd448ce14</value>
                    |        </field>
                    |        <field>
                    |          <name>__metadata_last_modified</name>
                    |          <value>2020-02-24T16:11:39.192Z</value>
                    |        </field>
                    |        <field>
                    |          <name>__parent_collection_size</name>
                    |          <value>0</value>
                    |        </field>
                    |        <field>
                    |          <name>__child_collection_size</name>
                    |          <value>4</value>
                    |        </field>
                    |        <field>
                    |          <name>__child_collection</name>
                    |          <value>KP-54407</value>
                    |        </field>
                    |        <field>
                    |          <name>__child_collection</name>
                    |          <value>KP-54421</value>
                    |        </field>
                    |        <field>
                    |          <name>__child_collection</name>
                    |          <value>KP-54426</value>
                    |        </field>
                    |        <field>
                    |          <name>__child_collection</name>
                    |          <value>KP-54432</value>
                    |        </field>
                    |        <field>
                    |          <name>__items_size</name>
                    |          <value>0</value>
                    |        </field>
                    |        <field>
                    |          <name>__ancestor_collection_size</name>
                    |          <value>0</value>
                    |        </field>
                    |        <field>
                    |          <name>__folder_mapped</name>
                    |          <value>false</value>
                    |        </field>
                    |      </timespan>
                    |    </metadata>
                    |  </collection>
                    |</CollectionListDocument>""".stripMargin

  "PlutoCommissionSource" should {
    "yield a list of PlutoCommission objects" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val mockedComm = mock[VSCommunicator]

      mockedComm.request(any,any,any,any,any,any)(any,any) returns Future(Right(sampleXml)) thenReturn Future(Right("<CollectionListDocument/>"))

      val sinkFact = Sink.seq[PlutoCommission]

      val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(new PlutoCommissionSource())
        src ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run, 5 seconds)

      result.headOption must beSome(
        PlutoCommission("KP-54431","March Agency Sport 2020",List(),"In production",Some(LocalDate.of(2021,3,1)))
      )
      result(1) mustEqual PlutoCommission("KP-54406","24 January 2020 Weekly News Agency",List("KP-54407","KP-54421","KP-54426","KP-54432"),"In production",Some(LocalDate.of(2020,3,1)))

      result.length mustEqual 2

    }
  }
}
