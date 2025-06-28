package Database

import cats.effect.IO
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import Config.ServerConfig
import java.sql.{Connection, ResultSet, PreparedStatement}
import cats.effect.Resource

class DatabaseConnection(config: ServerConfig) {
  
  private val hikariConfig = new HikariConfig()
  hikariConfig.setJdbcUrl(s"${config.jdbcUrl}galphos")
  hikariConfig.setUsername(config.username)
  hikariConfig.setPassword(config.password)
  hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
  hikariConfig.setConnectionTimeout(config.connectionLiveMinutes * 60 * 1000)
  hikariConfig.setIdleTimeout(config.connectionLiveMinutes * 60 * 1000)
  hikariConfig.setMaxLifetime(config.connectionLiveMinutes * 2 * 60 * 1000)
  hikariConfig.setDriverClassName("org.postgresql.Driver")
  
  private val dataSource = new HikariDataSource(hikariConfig)
  
  def getConnection: Resource[IO, Connection] = {
    Resource.make(IO.blocking(dataSource.getConnection))(conn => IO.blocking(conn.close()))
  }
  
  def executeQuery[T](sql: String, params: List[Any] = List.empty)(processor: ResultSet => T): IO[List[T]] = {
    getConnection.use { conn =>
      IO.blocking {
        val stmt = conn.prepareStatement(sql)
        
        // Set parameters
        params.zipWithIndex.foreach { case (param, index) =>
          param match {
            case s: String => stmt.setString(index + 1, s)
            case i: Int => stmt.setInt(index + 1, i)
            case l: Long => stmt.setLong(index + 1, l)
            case uuid: java.util.UUID => stmt.setObject(index + 1, uuid)
            case opt: Option[_] => opt match {
              case Some(value) => value match {
                case s: String => stmt.setString(index + 1, s)
                case i: Int => stmt.setInt(index + 1, i)
                case l: Long => stmt.setLong(index + 1, l)
                case uuid: java.util.UUID => stmt.setObject(index + 1, uuid)
                case _ => stmt.setObject(index + 1, value)
              }
              case None => stmt.setNull(index + 1, java.sql.Types.NULL)
            }
            case _ => stmt.setObject(index + 1, param)
          }
        }
        
        val rs = stmt.executeQuery()
        val results = scala.collection.mutable.ListBuffer[T]()
        
        while (rs.next()) {
          results += processor(rs)
        }
        
        rs.close()
        stmt.close()
        results.toList
      }
    }
  }
  
  def executeUpdate(sql: String, params: List[Any] = List.empty): IO[Int] = {
    getConnection.use { conn =>
      IO.blocking {
        val stmt = conn.prepareStatement(sql)
        
        // Set parameters
        params.zipWithIndex.foreach { case (param, index) =>
          param match {
            case s: String => stmt.setString(index + 1, s)
            case i: Int => stmt.setInt(index + 1, i)
            case l: Long => stmt.setLong(index + 1, l)
            case uuid: java.util.UUID => stmt.setObject(index + 1, uuid)
            case opt: Option[_] => opt match {
              case Some(value) => value match {
                case s: String => stmt.setString(index + 1, s)
                case i: Int => stmt.setInt(index + 1, i)
                case l: Long => stmt.setLong(index + 1, l)
                case uuid: java.util.UUID => stmt.setObject(index + 1, uuid)
                case _ => stmt.setObject(index + 1, value)
              }
              case None => stmt.setNull(index + 1, java.sql.Types.NULL)
            }
            case _ => stmt.setObject(index + 1, param)
          }
        }
        
        val result = stmt.executeUpdate()
        stmt.close()
        result
      }
    }
  }
  
  def executeInsertWithReturn[T](sql: String, params: List[Any], returnColumns: List[String])(processor: ResultSet => T): IO[Option[T]] = {
    getConnection.use { conn =>
      IO.blocking {
        val stmt = conn.prepareStatement(sql, returnColumns.toArray)
        
        // Set parameters
        params.zipWithIndex.foreach { case (param, index) =>
          param match {
            case s: String => stmt.setString(index + 1, s)
            case i: Int => stmt.setInt(index + 1, i)
            case l: Long => stmt.setLong(index + 1, l)
            case uuid: java.util.UUID => stmt.setObject(index + 1, uuid)
            case opt: Option[_] => opt match {
              case Some(value) => value match {
                case s: String => stmt.setString(index + 1, s)
                case i: Int => stmt.setInt(index + 1, i)
                case l: Long => stmt.setLong(index + 1, l)
                case uuid: java.util.UUID => stmt.setObject(index + 1, uuid)
                case _ => stmt.setObject(index + 1, value)
              }
              case None => stmt.setNull(index + 1, java.sql.Types.NULL)
            }
            case _ => stmt.setObject(index + 1, param)
          }
        }
        
        stmt.executeUpdate()
        val rs = stmt.getGeneratedKeys
        
        val result = if (rs.next()) {
          Some(processor(rs))
        } else {
          None
        }
        
        rs.close()
        stmt.close()
        result
      }
    }
  }
  
  def close(): IO[Unit] = {
    IO.blocking(dataSource.close())
  }
}
