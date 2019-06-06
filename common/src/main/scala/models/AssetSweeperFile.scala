package models

import java.sql.ResultSet
import java.time.{ZoneId, ZonedDateTime}

import scala.util.Try

case class AssetSweeperFile(id:Long, filepath:String, filename:String, mtime:ZonedDateTime, ctime:ZonedDateTime,
                            atime:ZonedDateTime, imported_id:Option[String], importedAt:Option[ZonedDateTime],
                            size:Long, owner:Int, gid:Int, prelude_ref:Option[String],
                            ignore:Boolean, mime_type:String, asset_folder:Option[String])

object AssetSweeperFile extends ((Long,String,String,ZonedDateTime,ZonedDateTime,ZonedDateTime,Option[String],Option[ZonedDateTime],Long,Int,Int,Option[String], Boolean, String, Option[String])=>AssetSweeperFile)
{
  def fromResultSet(resultSet:ResultSet):Try[AssetSweeperFile] = Try {
      new AssetSweeperFile(
        resultSet.getLong("id"),
        resultSet.getString("filepath"),
        resultSet.getString("filename"),
        ZonedDateTime.ofInstant(resultSet.getTimestamp("mtime").toInstant, ZoneId.of("UTC")),
        ZonedDateTime.ofInstant(resultSet.getTimestamp("ctime").toInstant, ZoneId.of("UTC")),
        ZonedDateTime.ofInstant(resultSet.getTimestamp("atime").toInstant, ZoneId.of("UTC")),
        Option(resultSet.getString("imported_id")),
        Option(resultSet.getTimestamp("imported_at")).map(ts=>ZonedDateTime.ofInstant(ts.toInstant, ZoneId.of("UTC"))),
        resultSet.getLong("size"),
        resultSet.getInt("owner"),
        resultSet.getInt("gid"),
        Option(resultSet.getString("prelude_ref")),
        resultSet.getBoolean("ignore"),
        resultSet.getString("mime_type"),
        Option(resultSet.getString("asset_folder"))
      )
    }

  def fromDeletionTableResultSet(resultSet:ResultSet):Try[AssetSweeperFile] = Try {
    new AssetSweeperFile(
      resultSet.getLong("id"),
      resultSet.getString("filepath"),
      resultSet.getString("filename"),
      ZonedDateTime.ofInstant(resultSet.getTimestamp("mtime").toInstant, ZoneId.of("UTC")),
      ZonedDateTime.ofInstant(resultSet.getTimestamp("ctime").toInstant, ZoneId.of("UTC")),
      ZonedDateTime.ofInstant(resultSet.getTimestamp("atime").toInstant, ZoneId.of("UTC")),
      Option(resultSet.getString("imported_id")),
      Option(resultSet.getTimestamp("imported_at")).map(ts=>ZonedDateTime.ofInstant(ts.toInstant, ZoneId.of("UTC"))),
      resultSet.getLong("size"),
      resultSet.getInt("owner"),
      resultSet.getInt("gid"),
      Option(resultSet.getString("prelude_ref")),
      resultSet.getBoolean("ignore"),
      resultSet.getString("mime_type"),
      None
    )
  }
}