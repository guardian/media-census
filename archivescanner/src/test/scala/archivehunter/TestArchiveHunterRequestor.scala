package archivehunter

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class TestArchiveHunterRequestor extends Specification with Mockito {
  "ArchiveHunterRequestor.makeAuth" should {
    "sign a pre-existing Request with current date and signature" in {
      val rq = new ArchiveHunterRequestor("https://archivehunter.company.org","secretkey")(mock[ActorSystem],mock[Materializer]) {
        override def currentTimeString: String = ZonedDateTime.parse("2019-04-05T01:02:03Z").format(DateTimeFormatter.RFC_1123_DATE_TIME)
      }

      val initialRequest = HttpRequest()
      val result = rq.makeAuth(initialRequest)

      println(result.getHeaders())
      result.getHeader("Authorization").get().value() mustEqual "HMAC Y/ZHJ0K8Xe6OKsObyljzp6U03lSNm/hS1HDA2+KjYME="
      result.getHeader("Date").get().value() mustEqual "Fri, 5 Apr 2019 01:02:03 GMT"
    }
  }
}
