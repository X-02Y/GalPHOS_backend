package Database

import Config.{ServiceConfig, DatabaseConfig}
import cats.effect.{IO, Resource}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.circe.*
import io.circe.parser.*
import org.slf4j.LoggerFactory
import java.sql.{Connection, PreparedStatement, ResultSet}
import javax.sql.DataSource

case class SqlParameter(paramType: String, value: Any)

object DatabaseManager {
  private val logger = LoggerFactory.getLogger("DatabaseManager")

  def createDataSource(config: DatabaseConfig): Resource[IO, DataSource] = {
    Resource.make(
      IO {
        val hikariConfig = new HikariConfig()
        hikariConfig.setJdbcUrl(config.jdbcUrl)
        hikariConfig.setUsername(config.username)
        hikariConfig.setPassword(config.password)
        hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
        hikariConfig.setConnectionTestQuery("SELECT 1")
        hikariConfig.setPoolName("UserManagementPool")
        
        // 设置连接存活时间
        hikariConfig.setMaxLifetime(config.connectionLiveMinutes * 60 * 1000L)
        
        // 设置预处理语句缓存
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", config.prepStmtCacheSize)
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", config.prepStmtCacheSqlLimit)
        
        val dataSource = new HikariDataSource(hikariConfig)
        logger.info(s"数据库连接池已创建: ${config.jdbcUrl}")
        dataSource
      }
    )(dataSource => IO {
      dataSource.close()
      logger.info("数据库连接池已关闭")
    })
  }

  private var dataSourceInstance: Option[DataSource] = None

  def initializeDataSource(config: DatabaseConfig): IO[Unit] = {
    createDataSource(config).allocated.map { case (ds, _) =>
      dataSourceInstance = Some(ds)
      logger.info("数据库连接池初始化完成")
    }
  }

  private def getDataSource: DataSource = {
    dataSourceInstance.getOrElse(
      throw new RuntimeException("数据库连接池未初始化")
    )
  }

  def executeQuery(sql: String, params: List[SqlParameter] = List.empty): IO[List[Json]] = {
    IO.blocking {
      val connection = getDataSource.getConnection
      try {
        val statement = connection.prepareStatement(sql)
        setParameters(statement, params)
        
        logger.debug(s"执行查询: $sql")
        val resultSet = statement.executeQuery()
        val results = convertResultSetToJson(resultSet)
        logger.debug(s"查询返回 ${results.length} 条记录")
        results
      } finally {
        connection.close()
      }
    }
  }

  def executeQueryOptional(sql: String, params: List[SqlParameter] = List.empty): IO[Option[Json]] = {
    executeQuery(sql, params).map(_.headOption)
  }

  def executeUpdate(sql: String, params: List[SqlParameter] = List.empty): IO[Int] = {
    IO.blocking {
      val connection = getDataSource.getConnection
      try {
        val statement = connection.prepareStatement(sql)
        setParameters(statement, params)
        
        logger.debug(s"执行更新: $sql")
        val rowsAffected = statement.executeUpdate()
        logger.debug(s"更新影响 $rowsAffected 行")
        rowsAffected
      } finally {
        connection.close()
      }
    }
  }

  def executeInsertWithGeneratedKey(sql: String, params: List[SqlParameter] = List.empty): IO[String] = {
    IO.blocking {
      val connection = getDataSource.getConnection
      try {
        val statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
        setParameters(statement, params)
        
        logger.debug(s"执行插入: $sql")
        statement.executeUpdate()
        
        val generatedKeys = statement.getGeneratedKeys
        if (generatedKeys.next()) {
          val key = generatedKeys.getString(1)
          logger.debug(s"插入成功，生成的key: $key")
          key
        } else {
          throw new RuntimeException("插入失败，未获取到生成的key")
        }
      } finally {
        connection.close()
      }
    }
  }

  private def setParameters(statement: PreparedStatement, params: List[SqlParameter]): Unit = {
    params.zipWithIndex.foreach { case (param, index) =>
      val position = index + 1
      param.paramType.toLowerCase match {
        case "string" => statement.setString(position, param.value.toString)
        case "int" => statement.setInt(position, param.value.asInstanceOf[Int])
        case "long" => statement.setLong(position, param.value.asInstanceOf[Long])
        case "boolean" => statement.setBoolean(position, param.value.asInstanceOf[Boolean])
        case "timestamp" => statement.setTimestamp(position, param.value.asInstanceOf[java.sql.Timestamp])
        case _ => statement.setObject(position, param.value)
      }
    }
  }

  private def convertResultSetToJson(resultSet: ResultSet): List[Json] = {
    val metaData = resultSet.getMetaData
    val columnCount = metaData.getColumnCount
    val columnNames = (1 to columnCount).map(metaData.getColumnName)

    val results = scala.collection.mutable.ListBuffer[Json]()
    
    while (resultSet.next()) {
      val row = columnNames.map { columnName =>
        val value = Option(resultSet.getObject(columnName)) match {
          case None => Json.Null
          case Some(v: String) => Json.fromString(v)
          case Some(v: Int) => Json.fromInt(v)
          case Some(v: Long) => Json.fromLong(v)
          case Some(v: Double) => Json.fromDoubleOrNull(v)
          case Some(v: Boolean) => Json.fromBoolean(v)
          case Some(v: java.sql.Timestamp) => Json.fromString(v.toString)
          case Some(v) => Json.fromString(v.toString)
        }
        columnName -> value
      }.toMap
      
      results += Json.fromFields(row)
    }
    
    results.toList
  }

  // 解码字段值的辅助方法
  def decodeField[T](json: Json, fieldName: String)(implicit decoder: Decoder[T]): Either[String, T] = {
    json.hcursor.downField(fieldName).as[T].left.map(_.getMessage)
  }

  def decodeFieldUnsafe[T](json: Json, fieldName: String)(implicit decoder: Decoder[T]): T = {
    decodeField[T](json, fieldName) match {
      case Right(value) => value
      case Left(error) => throw new RuntimeException(s"解码字段 $fieldName 失败: $error")
    }
  }

  def decodeFieldOptional[T](json: Json, fieldName: String)(implicit decoder: Decoder[T]): Option[T] = {
    json.hcursor.downField(fieldName).as[Option[T]].getOrElse(None)
  }
}
