package archivehunter

import java.net.URI

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class ArchiveHunterLookupSpec extends Specification with Mockito {
  "ArchiveHunterLookup.extractPathPart" should {
    "extract relevant parts from a url path" in {
      implicit val mockActorSystem = mock[ActorSystem]
      implicit val mockMaterializer = mock[ActorMaterializer]

      val testUri = new URI("omms://2E4907FC-1955-4B8B-84C9-2DB4959E1E0A:sdfasdgsagsdgasfdgdfgdfgdsg@1.2.3.4/2EA517AF-A60C-44AC-847E-98E33D1E1650/6ED45375-B0CB-4159-BED7-9CB4EBF284A2/Multimedia_Culture_and_Life/Film_Junket_interviews_2016/richard_sprenger_Tom_Hiddleston_I_Saw_the_Light/Interview/private/M4ROOT/STATUS.BIN")
      val toTest = new ArchiveHunterLookup("ignored","ignored")
      val result = toTest.extractPathPart(testUri)

      result mustEqual "Multimedia_Culture_and_Life/Film_Junket_interviews_2016/richard_sprenger_Tom_Hiddleston_I_Saw_the_Light/Interview/private/M4ROOT/STATUS.BIN"
    }
  }
}
