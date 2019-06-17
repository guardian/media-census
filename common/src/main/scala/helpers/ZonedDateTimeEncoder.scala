package helpers

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

trait ZonedDateTimeEncoder {
  implicit val encodeZonedDateTime: Encoder[ZonedDateTime] = (a: ZonedDateTime) => Json.fromString(a.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))

  implicit val decodeZonedDateTime: Decoder[ZonedDateTime] = (c: HCursor) => for {
    str <- c.value.as[String]
  } yield ZonedDateTime.parse(str, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
