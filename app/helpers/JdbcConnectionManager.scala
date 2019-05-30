package helpers

import javax.inject.Inject
import play.api.Configuration
import java.sql.DriverManager
import java.sql.Connection
import scala.util.Try

class JdbcConnectionManager @Inject() (config:Configuration) {
  def getConnectionForSection(configSection:String):Try[Connection] = Try {
    val driver = config.get[String](s"$configSection.driver")
    val url = config.get[String](s"$configSection.jdbcUrl")
    val username = config.get[String](s"$configSection.user")
    val password = config.get[String](s"$configSection.password")

    //load the java class into memory
    Class.forName(driver)
    DriverManager.getConnection(url, username, password)
  }

}
