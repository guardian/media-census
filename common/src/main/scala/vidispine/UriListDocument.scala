package vidispine

import com.softwaremill.sttp.Uri

import scala.util.Try
import scala.xml.{NodeSeq, XML}

case class UriListDocument (uri:Seq[String])

object UriListDocument {
  def fromXmlString(xmlString:String) = fromXml(XML.loadString(xmlString))

  def fromXml(elem: NodeSeq):Try[UriListDocument] = Try {
    new UriListDocument(
      (elem \ "uri").map(uriElem=>uriElem.text)
    )
  }
}