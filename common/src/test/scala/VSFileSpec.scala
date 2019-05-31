import org.specs2.mutable.Specification
import vidispine.VSFile

class VSFileSpec extends Specification{
  "VSFile.storageSubpath" should {
    "strip the storage path out of a fullpath" in {
      val result = VSFile.storageSubpath("/path/to/storage/with/media/on/it","/path/to/storage")
      result must beSome("with/media/on/it")
    }

    "test what happens if they don't overlap" in {
      val result = VSFile.storageSubpath("/path/to/storage/with/media/on/it","/completely/different/path")
      result must beNone
    }
  }
}
