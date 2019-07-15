package vidispine

import java.time.ZonedDateTime
import java.util.UUID

import scala.xml.NodeSeq

case class VSMetadataValue(value:String, uuid:UUID, user:String, timestamp:ZonedDateTime, change:String)
case class VSMetadataEntry(name:String, uuid:UUID, user:String, timestamp:ZonedDateTime, change:String, values:Seq[VSMetadataValue])

object VSMetadataEntry {
  /**
    * create a sequence of VSMetadataEntry objects from the provided parsed XML.
    * @param xml NodeSeq pointing to a "timespan" level of aVidispine MetadataDocument
    * @return a sequence of VSMetadataEntry objects
    */
  def fromXml(xml:NodeSeq):Seq[VSMetadataEntry] = {
    (xml \ "field").map(fieldNode=>
      new VSMetadataEntry((fieldNode \ "name").text,
        UUID.fromString(fieldNode \@ "uuid"),
        fieldNode \@ "user",
        ZonedDateTime.parse(fieldNode \@ "timestamp"),
        fieldNode \@ "change",
        (fieldNode \ "value").map(valueNode=>
          VSMetadataValue(
            valueNode.text,
            UUID.fromString(valueNode \@ "uuid"),
            valueNode \@ "user",
            ZonedDateTime.parse(fieldNode \@ "timestamp"),
            valueNode \@ "change"
          )
        )
      )

    )
  }
}