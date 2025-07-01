package Database

import cats.effect.IO
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import Config.DatabaseConfig
import io.circe.*
import io.circe.parser.*
import org.slf4j.LoggerFactory
import java.sql.{Connection, PreparedStatement, ResultSet, SQLException}
import javax.sql.DataSource
import scala.util.{Try, Using}

case class SqlParameter(dataType: String, value: Any)

object DatabaseManager {
  private val logger = LoggerFactory.getLogger("DatabaseManager")
  private var dataSource: Option[HikariDataSource] = None

  def initialize(config: DatabaseConfig): IO[Unit] = IO {
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.jdbcUrl)
    hikariConfig.setUsername(config.username)
    hikariConfig.setPassword(config.password)
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
    hikariConfig.addDataSourceProperty("prepStmtCacheSize", config.prepStmtCacheSize)
    hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", config.prepStmtCacheSqlLimit)
    hikariConfig.addDataSourceProperty("useServerPrepStmts", "true")
    hikariConfig.addDataSourceProperty("useLocalSessionState", "true")
    hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true")
    hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true")
    hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true")
    hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true")
    hikariConfig.addDataSourceProperty("maintainTimeStats", "false")

    dataSource = Some(new HikariDataSource(hikariConfig))
    logger.info("数据库连接池初始化成功")
  }

  def getConnection: IO[Connection] = IO {
    dataSource.getOrElse(throw new RuntimeException("数据库连接池未初始化")).getConnection
  }

  def close(): IO[Unit] = IO {
    dataSource.foreach(_.close())
    dataSource = None
    logger.info("数据库连接池已关闭")
  }

  // 执行查询并返回JSON结果列表
  def executeQuery(sql: String, params: List[SqlParameter] = List.empty): IO[List[Json]] = {
    for {
      connection <- getConnection
      result <- IO {
        try {
          val stmt = connection.prepareStatement(sql)
          try {
            setParameters(stmt, params)
            val rs = stmt.executeQuery()
            try {
              resultSetToJsonList(rs)
            } finally {
              rs.close()
            }
          } finally {
            stmt.close()
          }
        } finally {
          connection.close()
        }
      }.handleErrorWith { exception =>
        logger.error(s"查询执行失败: $sql", exception)
        IO.raiseError(exception)
      }
    } yield result
  }

  // 执行查询并返回可选的JSON结果
  def executeQueryOptional(sql: String, params: List[SqlParameter] = List.empty): IO[Option[Json]] = {
    executeQuery(sql, params).map(_.headOption)
  }

  // 执行更新操作
  def executeUpdate(sql: String, params: List[SqlParameter] = List.empty): IO[Int] = {
    for {
      connection <- getConnection
      result <- IO {
        try {
          val stmt = connection.prepareStatement(sql)
          try {
            setParameters(stmt, params)
            stmt.executeUpdate()
          } finally {
            stmt.close()
          }
        } finally {
          connection.close()
        }
      }.handleErrorWith { exception =>
        logger.error(s"更新执行失败: $sql", exception)
        IO.raiseError(exception)
      }
    } yield result
  }

  // 设置预处理语句参数
  private def setParameters(stmt: PreparedStatement, params: List[SqlParameter]): Unit = {
    params.zipWithIndex.foreach { case (param, index) =>
      val paramIndex = index + 1
      if (param.value == null) {
        stmt.setNull(paramIndex, java.sql.Types.NULL)
      } else {
        param.dataType.toLowerCase match {
          case "string" => stmt.setString(paramIndex, param.value.toString)
          case "int" | "integer" => stmt.setInt(paramIndex, param.value.asInstanceOf[Int])
          case "long" => stmt.setLong(paramIndex, param.value.asInstanceOf[Long])
          case "double" => stmt.setDouble(paramIndex, param.value.asInstanceOf[Double])
          case "boolean" => stmt.setBoolean(paramIndex, param.value.asInstanceOf[Boolean])
          case "timestamp" => 
            param.value match {
              case ts: java.sql.Timestamp => stmt.setTimestamp(paramIndex, ts)
              case ldt: java.time.LocalDateTime => 
                stmt.setTimestamp(paramIndex, java.sql.Timestamp.valueOf(ldt))
              case _ => stmt.setString(paramIndex, param.value.toString)
            }
          case _ => stmt.setObject(paramIndex, param.value)
        }
      }
    }
  }

  // 将ResultSet转换为JSON列表
  private def resultSetToJsonList(rs: ResultSet): List[Json] = {
    val metaData = rs.getMetaData
    val columnCount = metaData.getColumnCount
    val columnNames = (1 to columnCount).map(metaData.getColumnName).toList

    def rowToJson: Json = {
      val fields = columnNames.map { columnName =>
        val value = Option(rs.getObject(columnName)) match {
          case Some(v) => 
            v match {
              case s: String => Json.fromString(s)
              case i: java.lang.Integer => Json.fromInt(i.intValue())
              case l: java.lang.Long => Json.fromLong(l.longValue())
              case d: java.lang.Double => Json.fromDoubleOrNull(d.doubleValue())
              case b: java.lang.Boolean => Json.fromBoolean(b.booleanValue())
              case ts: java.sql.Timestamp => Json.fromString(ts.toString)
              case _ => Json.fromString(v.toString)
            }
          case None => Json.Null
        }
        columnName -> value
      }
      Json.obj(fields*)
    }

    var results = List.empty[Json]
    while (rs.next()) {
      results = rowToJson :: results
    }
    results.reverse
  }

  // 从JSON中解码字段
  def decodeField[T](json: Json, fieldName: String)(using decoder: Decoder[T]): Either[String, T] = {
    json.hcursor.downField(fieldName).as[T].left.map(_.message)
  }

  // 安全的字段解码，抛出异常
  def decodeFieldUnsafe[T](json: Json, fieldName: String)(using decoder: Decoder[T]): T = {
    decodeField[T](json, fieldName) match {
      case Right(value) => value
      case Left(error) => throw new RuntimeException(s"解码字段 '$fieldName' 失败: $error")
    }
  }

  // 可选字段解码，返回Option
  def decodeFieldOptional[T](json: Json, fieldName: String)(using decoder: Decoder[T]): Option[T] = {
    if (json.hcursor.downField(fieldName).focus.exists(_.isNull)) {
      None
    } else {
      decodeField[T](json, fieldName) match {
        case Right(value) => Some(value)
        case Left(_) => None
      }
    }
  }
}
