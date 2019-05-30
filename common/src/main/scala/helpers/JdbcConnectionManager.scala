package helpers
import java.sql.DriverManager
import java.sql.Connection
import config.DatabaseConfiguration

import scala.util.Try

object JdbcConnectionManager  {
  def getConnectionForSection(config:DatabaseConfiguration):Try[Connection] = Try {
    //load the java class into memory. Seems to be necessary according to the internets...
    Class.forName(config.driver)
    DriverManager.getConnection(config.jdbcUrl, config.user, config.password)
  }

}
