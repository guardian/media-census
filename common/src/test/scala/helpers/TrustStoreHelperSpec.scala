package helpers

import java.io.IOException
import java.security.cert.{CertificateException, X509Certificate}

import javax.net.ssl.X509TrustManager
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.util.Try

class TrustStoreHelperSpec extends Specification with Mockito {
  "TrustStoreHelper.getCustomTrustManager" should {
    "load in certs from a given path" in {
      if(System.getProperty("HAVE_TEST_KEYSTORE")!=null) {
        val result = TrustStoreHelper.getCustomTrustManager("keystore.jks", None)
        result must beSuccessfulTry[X509TrustManager]
        println(s"Got ${result.get.getAcceptedIssuers.length} issuers")
        result.get.getAcceptedIssuers.length must beGreaterThan(0)
      } else {
        println("TEST SKIPPED - Create a keystore.jks and set system property HAVE_TEST_KEYSTORE to apply it")
        1 mustEqual 1
      }
    }

    "pass on exceptions as a failure" in {
      val result = TrustStoreHelper.getCustomTrustManager("keystore.jks", Some("FSDfsdfsdfdsfsdfsdffsdf"))
      result must beFailedTry
      result.failed.get must beAnInstanceOf[IOException]
    }
  }

  "TrustStoreHelper.getDefaultTrustManager" should {
    "load in the default trust store" in {
      val result = TrustStoreHelper.getDefaultTrustManager
      result must beSuccessfulTry[X509TrustManager]
      println(s"Got ${result.get.getAcceptedIssuers.length} issuers")
      result.get.getAcceptedIssuers.length must beGreaterThan(10) //we can't reliably test for the _actual_ number of issuers on the system but if it's the default we expect to have a whole bunch there
    }
  }

  "TrustStoreHelper.customX509TrustStoreManager's custom manager" should {
    "delegate checkServerTrusted calls until one succeeds" in {
      val firstTm = mock[X509TrustManager]
      firstTm.checkServerTrusted(any,any) throws new CertificateException()

      val secondTm = mock[X509TrustManager]
      doNothing.when(secondTm).checkServerTrusted(any,any)

      val thirdTm = mock[X509TrustManager]
      thirdTm.checkServerTrusted(any,any) throws new CertificateException()

      val customTm = TrustStoreHelper.customX509TrustManager(Seq(firstTm,secondTm,thirdTm))

      val result = Try { customTm.checkServerTrusted(Array(),"something") }
      result must beSuccessfulTry
      there was one(firstTm).checkServerTrusted(Array(),"something")
      there was one(secondTm).checkServerTrusted(Array(),"something")
      there was no(thirdTm).checkServerTrusted(any,any)
    }

    "throw the last exception if all fail" in {
      val firstTm = mock[X509TrustManager]
      firstTm.checkServerTrusted(any,any) throws new CertificateException()

      val secondTm = mock[X509TrustManager]
      secondTm.checkServerTrusted(any,any) throws new CertificateException()

      val thirdTm = mock[X509TrustManager]
      thirdTm.checkServerTrusted(any,any) throws new CertificateException()

      val customTm = TrustStoreHelper.customX509TrustManager(Seq(firstTm,secondTm,thirdTm))

      val result = Try { customTm.checkServerTrusted(Array(),"something") }
      result must beFailedTry
      result.failed.get must beAnInstanceOf[CertificateException]
      there was one(firstTm).checkServerTrusted(Array(),"something")
      there was one(secondTm).checkServerTrusted(Array(),"something")
      there was one(thirdTm).checkServerTrusted(any,any)
    }

    "return a list of all the supported issuers" in {
      val mockedIssuers = Array(mock[X509Certificate],mock[X509Certificate],mock[X509Certificate],mock[X509Certificate],mock[X509Certificate])
      val firstTm = mock[X509TrustManager]
      firstTm.getAcceptedIssuers returns Array(mockedIssuers.head,mockedIssuers(1))

      val secondTm = mock[X509TrustManager]
      secondTm.getAcceptedIssuers returns Array(mockedIssuers(2))

      val thirdTm = mock[X509TrustManager]
      thirdTm.getAcceptedIssuers returns Array(mockedIssuers(3),mockedIssuers(4))

      val customTm = TrustStoreHelper.customX509TrustManager(Seq(firstTm,secondTm,thirdTm))
      val result = customTm.getAcceptedIssuers
      result mustEqual mockedIssuers
    }
  }
}
