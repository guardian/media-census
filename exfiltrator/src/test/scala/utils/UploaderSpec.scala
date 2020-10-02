package utils

import akka.stream.{ActorMaterializer, Materializer}
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSFile, VSLazyItem, VSShape}
import com.om.mxs.client.japi.UserInfo
import org.mockito.ArgumentMatchers.any
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import testhelpers.AkkaTestkitSpecs2Support

class UploaderSpec extends Specification with Mockito {
  "Uploader.isSensitive" should {
    "return true if the gnm_storage_rule_sensitive field is set" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val vsComm:VSCommunicator = mock[VSCommunicator]
      val fakeItem = mock[VSLazyItem]
      fakeItem.get(any) returns Some(Seq("sensitive"))

      val toTest = new Uploader(mock[UserInfo], "some-bucket", "proxy-bucket")
      val result = toTest.isSensitive(Some(fakeItem))
      result must beTrue
    }

    "return false if gnm_storage_rule_sensitive is set to an empty value" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val vsComm:VSCommunicator = mock[VSCommunicator]
      val fakeItem = mock[VSLazyItem]
      fakeItem.get(any) returns Some(Seq(""))

      val toTest = new Uploader(mock[UserInfo], "some-bucket", "proxy-bucket")
      val result = toTest.isSensitive(Some(fakeItem))
      result must beFalse
    }

    "return false if gnm_storage_rule_sensitive is not set" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val vsComm:VSCommunicator = mock[VSCommunicator]
      val fakeItem = mock[VSLazyItem]
      fakeItem.get(any) returns None

      val toTest = new Uploader(mock[UserInfo], "some-bucket", "proxy-bucket")
      val result = toTest.isSensitive(Some(fakeItem))
      result must beFalse
    }
  }

  "Uploader.findProxy" should {
    "return an empty list if the item is None" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val vsComm:VSCommunicator = mock[VSCommunicator]
      val fakeItem = mock[VSLazyItem]
      fakeItem.shapes returns None

      val toTest = new Uploader(mock[UserInfo], "some-bucket", "proxy-bucket")
      val result = toTest.findProxy(None)
      result mustEqual Seq()
    }

    "return an empty list if the item is has no shapes" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val vsComm:VSCommunicator = mock[VSCommunicator]
      val fakeItem = mock[VSLazyItem]
      fakeItem.shapes returns None

      val toTest = new Uploader(mock[UserInfo], "some-bucket", "proxy-bucket")
      val result = toTest.findProxy(Some(fakeItem))
      result mustEqual Seq()
    }

    "return an empty list if no listed shapes are on the list" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val vsComm:VSCommunicator = mock[VSCommunicator]
      val fakeItem = mock[VSLazyItem]
      fakeItem.shapes returns Some(Map(
        "dfjksd"->mock[VSShape],
        "djhsd"->mock[VSShape]
      ))

      val toTest = new Uploader(mock[UserInfo], "some-bucket", "proxy-bucket")
      val result = toTest.findProxy(Some(fakeItem))
      result mustEqual Seq()
    }

    "return an a proxy if that is in the list" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val vsComm:VSCommunicator = mock[VSCommunicator]

      val proxyShape = mock[VSShape]
      val proxyFile = mock[VSFile]
      proxyShape.files returns Seq(proxyFile, mock[VSFile])

      val fakeItem = mock[VSLazyItem]
      fakeItem.shapes returns Some(Map(
        "dfjksd"->mock[VSShape],
        "lowres"->proxyShape
      ))

      val toTest = new Uploader(mock[UserInfo], "some-bucket", "proxy-bucket")
      val result = toTest.findProxy(Some(fakeItem))
      result mustEqual Seq(proxyFile)
    }
  }
}
