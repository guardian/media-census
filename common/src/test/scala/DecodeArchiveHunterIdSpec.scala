import java.time.ZonedDateTime

import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import org.specs2.mutable.Specification
import streamComponents.DecodeArchiveHunterId
import testhelpers.AkkaTestkitSpecs2Support
import vidispine.VSFile

import scala.concurrent.Await
import scala.concurrent.duration._

class DecodeArchiveHunterIdSpec extends Specification {
  "DecodeArchiveHunterId" should {
    "decode the given ID and push it to the output" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      val fakeRecord = VSFile("VX-1234","/some/path","file://some/path",None,1234L,None,
        ZonedDateTime.now,1,"VX-23",None,None,
        Some("Z25tLW11bHRpbWVkaWEtZGVlcGFyY2hpdmU6TXVsdGltZWRpYV9OZXdzL1RvcF90ZW5fdmlyYWxfYW5pbWFsX3ZpZGVvc19vZl8yMDE1L2phbWVzX2J1bGxvY2tfVG9wX3Rlbl92aXJhbF9hbmltYWxfdmlkZW9zX29mXzIwMTUvQWRvYmUgUHJlbWllcmUgUHJvIEF1dG8tU2F2ZS9LUC00OTYwLTUucHJwcm9q"))

      val sinkFactory = Sink.fold[Seq[(VSFile,String,String)],(VSFile,String,String)](Seq())((acc,elem)=>acc:+elem)

      val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.single(fakeRecord))
        val toTest = builder.add(new DecodeArchiveHunterId)

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)
      result.length mustEqual 1

      result.head._1 mustEqual fakeRecord
      result.head._2 mustEqual "gnm-multimedia-deeparchive"
      result.head._3 mustEqual "Multimedia_News/Top_ten_viral_animal_videos_of_2015/james_bullock_Top_ten_viral_animal_videos_of_2015/Adobe Premiere Pro Auto-Save/KP-4960-5.prproj"
    }
  }
}
