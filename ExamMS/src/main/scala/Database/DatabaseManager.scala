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
    getConnection.flatMap { connection =>
      IO {
        try {
          val statement = connection.prepareStatement(sql)
          
          // 设置参数
          params.zipWithIndex.foreach { case (param, index) =>
            param.dataType.toLowerCase match {
              case "string" => statement.setString(index + 1, param.value.toString)
              case "int" => statement.setInt(index + 1, param.value.toString.toInt)
              case "long" => statement.setLong(index + 1, param.value.toString.toLong)
              case "double" => statement.setDouble(index + 1, param.value.toString.toDouble)
              case "boolean" => statement.setBoolean(index + 1, param.value.toString.toBoolean)
              case "uuid" => statement.setObject(index + 1, java.util.UUID.fromString(param.value.toString))
              case "timestamp" => statement.setTimestamp(index + 1, java.sql.Timestamp.valueOf(param.value.toString))
              case "bigdecimal" => statement.setBigDecimal(index + 1, new java.math.BigDecimal(param.value.toString))
              case _ => statement.setObject(index + 1, param.value)
            }
          }
          
          val resultSet = statement.executeQuery()
          val results = scala.collection.mutable.ListBuffer[Json]()
          
          while (resultSet.next()) {
            val metadata = resultSet.getMetaData
            val columnCount = metadata.getColumnCount
            val row = scala.collection.mutable.Map[String, Json]()
            
            for (i <- 1 to columnCount) {
              val columnName = metadata.getColumnName(i)
              val columnType = metadata.getColumnTypeName(i)
              val value = resultSet.getObject(i)
              
              val jsonValue = if (value == null) {
                Json.Null
              } else {
                columnType.toLowerCase match {
                  case "varchar" | "text" | "char" => Json.fromString(value.toString)
                  case "int4" | "integer" => Json.fromInt(value.toString.toInt)
                  case "int8" | "bigint" => Json.fromLong(value.toString.toLong)
                  case "numeric" | "decimal" => Json.fromBigDecimal(BigDecimal(value.toString))
                  case "bool" | "boolean" => Json.fromBoolean(value.toString.toBoolean)
                  case "timestamp" | "timestamptz" => Json.fromString(value.toString)
                  case "uuid" => Json.fromString(value.toString)
                  case "jsonb" | "json" =>
                    parse(value.toString).getOrElse(Json.fromString(value.toString))
                  case _ => Json.fromString(value.toString)
                }
              }
              row(columnName) = jsonValue
            }
            results += Json.fromFields(row.toSeq)
          }
          
          resultSet.close()
          statement.close()
          connection.close()
          
          results.toList
        } catch {
          case e: Exception =>
            connection.close()
            logger.error("数据库查询错误", e)
            throw e
        }
      }
    }
  }

  // 执行更新操作（INSERT、UPDATE、DELETE）
  def executeUpdate(sql: String, params: List[SqlParameter] = List.empty): IO[Int] = {
    getConnection.flatMap { connection =>
      IO {
        try {
          val statement = connection.prepareStatement(sql)
          
          // 设置参数
          params.zipWithIndex.foreach { case (param, index) =>
            param.dataType.toLowerCase match {
              case "string" => statement.setString(index + 1, param.value.toString)
              case "int" => statement.setInt(index + 1, param.value.toString.toInt)
              case "long" => statement.setLong(index + 1, param.value.toString.toLong)
              case "double" => statement.setDouble(index + 1, param.value.toString.toDouble)
              case "boolean" => statement.setBoolean(index + 1, param.value.toString.toBoolean)
              case "uuid" => statement.setObject(index + 1, java.util.UUID.fromString(param.value.toString))
              case "timestamp" => statement.setTimestamp(index + 1, java.sql.Timestamp.valueOf(param.value.toString))
              case "bigdecimal" => statement.setBigDecimal(index + 1, new java.math.BigDecimal(param.value.toString))
              case _ => statement.setObject(index + 1, param.value)
            }
          }
          
          val rowsAffected = statement.executeUpdate()
          
          statement.close()
          connection.close()
          
          rowsAffected
        } catch {
          case e: Exception =>
            connection.close()
            logger.error("数据库更新错误", e)
            throw e
        }
      }
    }
  }

  // 执行插入操作并返回生成的主键
  def executeInsertWithGeneratedKey(sql: String, params: List[SqlParameter] = List.empty): IO[Option[String]] = {
    getConnection.flatMap { connection =>
      IO {
        try {
          val statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
          
          // 设置参数
          params.zipWithIndex.foreach { case (param, index) =>
            param.dataType.toLowerCase match {
              case "string" => statement.setString(index + 1, param.value.toString)
              case "int" => statement.setInt(index + 1, param.value.toString.toInt)
              case "long" => statement.setLong(index + 1, param.value.toString.toLong)
              case "double" => statement.setDouble(index + 1, param.value.toString.toDouble)
              case "boolean" => statement.setBoolean(index + 1, param.value.toString.toBoolean)
              case "uuid" => statement.setObject(index + 1, java.util.UUID.fromString(param.value.toString))
              case "timestamp" => statement.setTimestamp(index + 1, java.sql.Timestamp.valueOf(param.value.toString))
              case "bigdecimal" => statement.setBigDecimal(index + 1, new java.math.BigDecimal(param.value.toString))
              case _ => statement.setObject(index + 1, param.value)
            }
          }
          
          statement.executeUpdate()
          val generatedKeys = statement.getGeneratedKeys()
          val result = if (generatedKeys.next()) {
            Some(generatedKeys.getString(1))
          } else {
            None
          }
          
          generatedKeys.close()
          statement.close()
          connection.close()
          
          result
        } catch {
          case e: Exception =>
            connection.close()
            logger.error("数据库插入错误", e)
            throw e
        }
      }
    }
  }

  // 执行事务
  def executeTransaction(operations: List[(String, List[SqlParameter])]): IO[Boolean] = {
    getConnection.flatMap { connection =>
      IO {
        try {
          connection.setAutoCommit(false)
          
          operations.foreach { case (sql, params) =>
            val statement = connection.prepareStatement(sql)
            
            params.zipWithIndex.foreach { case (param, index) =>
              param.dataType.toLowerCase match {
                case "string" => statement.setString(index + 1, param.value.toString)
                case "int" => statement.setInt(index + 1, param.value.toString.toInt)
                case "long" => statement.setLong(index + 1, param.value.toString.toLong)
                case "double" => statement.setDouble(index + 1, param.value.toString.toDouble)
                case "boolean" => statement.setBoolean(index + 1, param.value.toString.toBoolean)
                case "uuid" => statement.setObject(index + 1, java.util.UUID.fromString(param.value.toString))
                case "timestamp" => statement.setTimestamp(index + 1, java.sql.Timestamp.valueOf(param.value.toString))
                case "bigdecimal" => statement.setBigDecimal(index + 1, new java.math.BigDecimal(param.value.toString))
                case _ => statement.setObject(index + 1, param.value)
              }
            }
            
            statement.executeUpdate()
            statement.close()
          }
          
          connection.commit()
          connection.setAutoCommit(true)
          connection.close()
          
          true
        } catch {
          case e: Exception =>
            connection.rollback()
            connection.setAutoCommit(true)
            connection.close()
            logger.error("数据库事务错误", e)
            false
        }
      }
    }
  }
}
