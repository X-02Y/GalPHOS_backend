package Database

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import Config.{DatabaseConfig, ServerConfig}
import org.slf4j.LoggerFactory
import java.sql.{Connection, PreparedStatement, ResultSet, Timestamp, Statement}
import java.time.LocalDateTime
import java.util.UUID
import io.circe.*
import io.circe.parser.*
import scala.util.{Try, Success, Failure}

case class SqlParameter(paramType: String, value: Any)

object DatabaseManager {
  private val logger = LoggerFactory.getLogger("DatabaseManager")
  private var dataSourceOption: Option[HikariDataSource] = None

  def initialize(config: DatabaseConfig): IO[Unit] = IO {
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.jdbcUrl)
    hikariConfig.setUsername(config.username)
    hikariConfig.setPassword(config.password)
    hikariConfig.setDriverClassName("org.postgresql.Driver")
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
    hikariConfig.setMinimumIdle(2)
    hikariConfig.setConnectionTimeout(30000)
    hikariConfig.setIdleTimeout(config.connectionLiveMinutes * 60000)
    hikariConfig.setMaxLifetime(1800000)

    // 设置预编译语句缓存
    hikariConfig.addDataSourceProperty("cachePrepStmts", "true")
    hikariConfig.addDataSourceProperty("prepStmtCacheSize", config.prepStmtCacheSize.toString)
    hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", config.prepStmtCacheSqlLimit.toString)

    val dataSource = new HikariDataSource(hikariConfig)
    dataSourceOption = Some(dataSource)
    logger.info("数据库连接池初始化成功")
  }

  // 重载方法，直接从 ServerConfig 初始化
  def initialize(config: ServerConfig): IO[Unit] = initialize(config.toDatabaseConfig)

  def getConnection: Resource[IO, Connection] = {
    Resource.make(
      IO(dataSourceOption.get.getConnection)
    )(conn => IO(conn.close()))
  }

  def executeQuery(sql: String, params: List[SqlParameter] = List.empty): IO[List[Json]] = {
    getConnection.use { conn =>
      IO {
        val stmt = conn.prepareStatement(sql)
        setParameters(stmt, params)
        
        val rs = stmt.executeQuery()
        val results = scala.collection.mutable.ListBuffer[Json]()
        
        val metaData = rs.getMetaData
        val columnCount = metaData.getColumnCount
        
        while (rs.next()) {
          val row = scala.collection.mutable.Map[String, Json]()
          
          for (i <- 1 to columnCount) {
            val columnName = metaData.getColumnName(i).toLowerCase
            val value = rs.getObject(i)
            
            row(columnName) = value match {
              case null => Json.Null
              case s: String => Json.fromString(s)
              case i: Integer => Json.fromInt(i)
              case l: java.lang.Long => Json.fromLong(l)
              case d: java.math.BigDecimal => Json.fromBigDecimal(d)
              case b: java.lang.Boolean => Json.fromBoolean(b)
              case ts: Timestamp => Json.fromString(ts.toLocalDateTime.toString)
              case uuid: UUID => Json.fromString(uuid.toString)
              case other => Json.fromString(other.toString)
            }
          }
          
          results += Json.fromFields(row.toSeq)
        }
        
        results.toList
      }
    }
  }

  def executeUpdate(sql: String, params: List[SqlParameter] = List.empty): IO[Int] = {
    getConnection.use { conn =>
      IO {
        val stmt = conn.prepareStatement(sql)
        setParameters(stmt, params)
        stmt.executeUpdate()
      }
    }
  }

  def executeInsertWithGeneratedKey(sql: String, params: List[SqlParameter] = List.empty): IO[Option[String]] = {
    getConnection.use { conn =>
      IO {
        val stmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
        setParameters(stmt, params)
        val affectedRows = stmt.executeUpdate()
        
        if (affectedRows > 0) {
          val rs = stmt.getGeneratedKeys
          if (rs.next()) {
            Some(rs.getObject(1).toString)
          } else None
        } else None
      }
    }
  }

  private def setParameters(stmt: PreparedStatement, params: List[SqlParameter]): Unit = {
    params.zipWithIndex.foreach { case (param, index) =>
      val position = index + 1
      param.paramType.toLowerCase match {
        case "string" => stmt.setString(position, param.value.asInstanceOf[String])
        case "int" => stmt.setInt(position, param.value.asInstanceOf[Int])
        case "long" => stmt.setLong(position, param.value.asInstanceOf[Long])
        case "bigdecimal" => stmt.setBigDecimal(position, param.value.asInstanceOf[java.math.BigDecimal])
        case "boolean" => stmt.setBoolean(position, param.value.asInstanceOf[Boolean])
        case "timestamp" => 
          val dateTime = param.value.asInstanceOf[LocalDateTime]
          stmt.setTimestamp(position, Timestamp.valueOf(dateTime))
        case "uuid" => 
          val uuid = param.value match {
            case s: String => UUID.fromString(s)
            case u: UUID => u
            case _ => throw new IllegalArgumentException(s"Invalid UUID: ${param.value}")
          }
          stmt.setObject(position, uuid)
        case _ => stmt.setObject(position, param.value)
      }
    }
  }

  // 解码辅助方法
  def decodeFieldUnsafe[T](json: Json, fieldName: String)(implicit decoder: Decoder[T]): T = {
    json.hcursor.get[T](fieldName) match {
      case Right(value) => value
      case Left(error) => throw new RuntimeException(s"解码字段 $fieldName 失败: $error")
    }
  }

  def decodeFieldOptional[T](json: Json, fieldName: String)(implicit decoder: Decoder[T]): Option[T] = {
    json.hcursor.get[Option[T]](fieldName) match {
      case Right(value) => value
      case Left(_) => None
    }
  }

  def shutdown(): IO[Unit] = IO {
    dataSourceOption.foreach(_.close())
    dataSourceOption = None
    logger.info("数据库连接池已关闭")
  }
}
