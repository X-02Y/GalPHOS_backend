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
    logger.info("System Config Service - 数据库连接池初始化成功")
  }

  def getConnection: IO[Connection] = IO {
    dataSource.getOrElse(throw new RuntimeException("数据库连接池未初始化")).getConnection
  }

  def close(): IO[Unit] = IO {
    dataSource.foreach(_.close())
    dataSource = None
    logger.info("System Config Service - 数据库连接池已关闭")
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

  // 批量执行更新操作
  def executeBatch(sql: String, paramsList: List[List[SqlParameter]]): IO[Array[Int]] = {
    for {
      connection <- getConnection
      result <- IO {
        try {
          val stmt = connection.prepareStatement(sql)
          try {
            paramsList.foreach { params =>
              setParameters(stmt, params)
              stmt.addBatch()
            }
            stmt.executeBatch()
          } finally {
            stmt.close()
          }
        } finally {
          connection.close()
        }
      }.handleErrorWith { exception =>
        logger.error(s"批量更新执行失败: $sql", exception)
        IO.raiseError(exception)
      }
    } yield result
  }

  // 设置PreparedStatement参数
  private def setParameters(stmt: PreparedStatement, params: List[SqlParameter]): Unit = {
    params.zipWithIndex.foreach { case (param, index) =>
      val position = index + 1
      param.dataType.toLowerCase match {
        case "string" => stmt.setString(position, param.value.toString)
        case "int" => stmt.setInt(position, param.value.asInstanceOf[Int])
        case "long" => stmt.setLong(position, param.value.asInstanceOf[Long])
        case "boolean" => stmt.setBoolean(position, param.value.asInstanceOf[Boolean])
        case "double" => stmt.setDouble(position, param.value.asInstanceOf[Double])
        case "timestamp" => stmt.setTimestamp(position, param.value.asInstanceOf[java.sql.Timestamp])
        case _ => stmt.setObject(position, param.value)
      }
    }
  }

  // 将ResultSet转换为JSON列表
  private def resultSetToJsonList(rs: ResultSet): List[Json] = {
    val metaData = rs.getMetaData
    val columnCount = metaData.getColumnCount
    val columnNames = (1 to columnCount).map(metaData.getColumnName)

    val results = scala.collection.mutable.ListBuffer[Json]()
    while (rs.next()) {
      val jsonObject = columnNames.map { columnName =>
        val value = rs.getObject(columnName)
        val jsonValue = value match {
          case null => Json.Null
          case s: String => Json.fromString(s)
          case i: java.lang.Integer => Json.fromInt(i)
          case l: java.lang.Long => Json.fromLong(l)
          case d: java.lang.Double => Json.fromDoubleOrNull(d)
          case b: java.lang.Boolean => Json.fromBoolean(b)
          case ts: java.sql.Timestamp => Json.fromString(ts.toString)
          case dt: java.sql.Date => Json.fromString(dt.toString)
          case _ => Json.fromString(value.toString)
        }
        columnName.toLowerCase -> jsonValue
      }.toMap
      results += Json.fromFields(jsonObject)
    }
    results.toList
  }

  // 从JSON中解码字段（不安全版本，会抛出异常）
  def decodeFieldUnsafe[T](json: Json, fieldName: String)(implicit decoder: Decoder[T]): T = {
    json.hcursor.downField(fieldName).as[T] match {
      case Right(value) => value
      case Left(error) => throw new RuntimeException(s"Failed to decode field '$fieldName': $error")
    }
  }

  // 从JSON中解码字段（安全版本）
  def decodeFieldOptional[T](json: Json, fieldName: String)(implicit decoder: Decoder[T]): Option[T] = {
    json.hcursor.downField(fieldName).as[T].toOption
  }

  // 检查数据库连接是否健康
  def healthCheck(): IO[Boolean] = {
    executeQuery("SELECT 1 as health_check").map(_.nonEmpty).handleErrorWith(_ => IO.pure(false))
  }
}
