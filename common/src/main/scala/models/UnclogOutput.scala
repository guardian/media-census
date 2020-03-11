package models
import io.circe.{Encoder, Decoder}

object MediaStatusValue extends Enumeration {
  val SHOULD_BE_ARCHIVED,NO_PROJECT,BRANDING,DELETABLE,SENSITIVE,PROJECT_HELD,PROJECT_OPEN,UNKNOWN,NO_ITEM = Value
}

object MediaStatusValueEncoder {
  implicit val mediaStatusValueEncoder = Encoder.enumEncoder(MediaStatusValue)
  implicit val mediaStatusValueDecoder = Decoder.enumDecoder(MediaStatusValue)
}

case class UnclogOutput (VSFileId:String, VSItemId:Option[String], FileSize:Long, ParentCollectionIds:Seq[String], MediaStatus:MediaStatusValue.Value)