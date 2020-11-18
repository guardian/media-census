package utils

import org.specs2.mutable.Specification

class ItemProjectSpec extends Specification {
  "ItemProject.findField" should {
    "return all values for a given piece of content" in {
      val fakeXml = <MetadataDocument xmlns="http://xml.vidispine.com/schema/vidispine">
        <timespan start="-INF" end="+INF">
          <field>
            <name>field_1</name>
            <value>value1</value>
            <value>value2</value>
          </field>
          <field>
            <name>field_2</name>
            <value>value2</value>
          </field>
          <field>
            <name>field_1</name>
            <value>value3</value>
          </field>
        </timespan>
      </MetadataDocument>

      val result = ItemProject.findField(fakeXml \ "timespan" \ "field", "field_1")
      result must beSome("value1 value2 value3")
    }

    "return a single value if that is what's present" in {
      val fakeXml = <MetadataDocument xmlns="http://xml.vidispine.com/schema/vidispine">
        <timespan start="-INF" end="+INF">
          <field>
            <name>field_1</name>
            <value>value1</value>
            <value>value2</value>
          </field>
          <field>
            <name>field_2</name>
            <value>value2</value>
          </field>
          <field>
            <name>field_1</name>
            <value>value3</value>
          </field>
        </timespan>
      </MetadataDocument>

      val result = ItemProject.findField(fakeXml \ "timespan" \ "field", "field_2")
      result must beSome("value2")
    }

    "return None if no field is present" in {
      val fakeXml = <MetadataDocument xmlns="http://xml.vidispine.com/schema/vidispine">
        <timespan start="-INF" end="+INF">
          <field>
            <name>field_1</name>
            <value>value1</value>
            <value>value2</value>
          </field>
          <field>
            <name>field_2</name>
            <value>value2</value>
          </field>
          <field>
            <name>field_1</name>
            <value>value3</value>
          </field>
        </timespan>
      </MetadataDocument>

      val result = ItemProject.findField(fakeXml \ "timespan" \ "field", "field_5")
      result must beNone
    }

    "return None if the field is present with no values" in {
      val fakeXml = <MetadataDocument xmlns="http://xml.vidispine.com/schema/vidispine">
        <timespan start="-INF" end="+INF">
          <field>
            <name>field_1</name>
            <value>value1</value>
            <value>value2</value>
          </field>
          <field>
            <name>field_2</name>
            <value>value2</value>
          </field>
          <field>
            <name>field_1</name>
            <value>value3</value>
          </field>
          <field>
            <name>field_5</name>
          </field>
        </timespan>
      </MetadataDocument>

      val result = ItemProject.findField(fakeXml \ "timespan" \ "field", "field_5")
      result must beNone
    }
  }

  "ItemProject.fieldAsBool" should {
    "return true if the field exists with a value" in {
      val fakeXml = <MetadataDocument xmlns="http://xml.vidispine.com/schema/vidispine">
        <timespan start="-INF" end="+INF">
          <field>
            <name>field_1</name>
            <value>value1</value>
            <value>value2</value>
          </field>
          <field>
            <name>field_2</name>
            <value>value2</value>
          </field>
          <field>
            <name>field_1</name>
            <value>value3</value>
          </field>
          <field>
            <name>field_5</name>
          </field>
        </timespan>
      </MetadataDocument>

      val result = ItemProject.fieldAsBool(fakeXml \ "timespan" \ "field", "field_2")
      result must beTrue
    }

    "return false if the field exists without a value" in {
      val fakeXml = <MetadataDocument xmlns="http://xml.vidispine.com/schema/vidispine">
        <timespan start="-INF" end="+INF">
          <field>
            <name>field_1</name>
            <value>value1</value>
            <value>value2</value>
          </field>
          <field>
            <name>field_2</name>
            <value>value2</value>
          </field>
          <field>
            <name>field_1</name>
            <value>value3</value>
          </field>
          <field>
            <name>field_5</name>
          </field>
        </timespan>
      </MetadataDocument>

      val result = ItemProject.fieldAsBool(fakeXml \ "timespan" \ "field", "field_5")
      result must beFalse
    }
  }
}
