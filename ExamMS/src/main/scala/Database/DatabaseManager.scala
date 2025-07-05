package Database

import cats.effect.IO
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import Config.DatabaseConfig
import org.slf4j.LoggerFactory
import java.sql.{Connection, ResultSet}
import javax.sql.DataSource

object DatabaseManager {
  private val logger = LoggerFactory.getLogger("DatabaseManager")
  private var dataSource: Option[HikariDataSource] = None

  def initializeDataSource(config: DatabaseConfig): IO[Unit] = IO {
    logger.info("初始化数据库连接池...")
    
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.jdbcUrl)
    hikariConfig.setUsername(config.username)
    hikariConfig.setPassword(config.password)
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
    hikariConfig.setMaxLifetime(config.connectionLiveMinutes * 60 * 1000L)
    hikariConfig.setConnectionInitSql("SELECT 1")
    hikariConfig.setDriverClassName("org.postgresql.Driver")
    
    // 连接池优化配置
    hikariConfig.addDataSourceProperty("cachePrepStmts", "true")
    hikariConfig.addDataSourceProperty("prepStmtCacheSize", config.prepStmtCacheSize.toString)
    hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", config.prepStmtCacheSqlLimit.toString)
    hikariConfig.addDataSourceProperty("useServerPrepStmts", "true")
    hikariConfig.addDataSourceProperty("useLocalSessionState", "true")
    hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true")
    hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true")
    hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true")
    hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true")
    hikariConfig.addDataSourceProperty("maintainTimeStats", "false")
    
    val ds = new HikariDataSource(hikariConfig)
    dataSource = Some(ds)
    logger.info("数据库连接池初始化完成")
  }

  def getDataSource: Option[DataSource] = dataSource

  def withConnection[T](operation: Connection => T): IO[T] = IO {
    dataSource match {
      case Some(ds) =>
        val connection = ds.getConnection()
        try {
          operation(connection)
        } finally {
          connection.close()
        }
      case None =>
        throw new RuntimeException("数据库连接池未初始化")
    }
  }

  def withTransaction[T](operation: Connection => T): IO[T] = IO {
    dataSource match {
      case Some(ds) =>
        val connection = ds.getConnection()
        try {
          connection.setAutoCommit(false)
          val result = operation(connection)
          connection.commit()
          result
        } catch {
          case ex: Exception =>
            connection.rollback()
            throw ex
        } finally {
          connection.setAutoCommit(true)
          connection.close()
        }
      case None =>
        throw new RuntimeException("数据库连接池未初始化")
    }
  }

  def shutdown(): IO[Unit] = IO {
    dataSource.foreach { ds =>
      logger.info("关闭数据库连接池...")
      ds.close()
      logger.info("数据库连接池已关闭")
    }
    dataSource = None
  }
}

// 数据库操作工具类
object DatabaseUtils {
  def executeQuery[T](sql: String, params: List[Any] = List.empty)(parser: ResultSet => T): IO[List[T]] = {
    DatabaseManager.withConnection { connection =>
      val statement = connection.prepareStatement(sql)
      try {
        // 设置参数
        params.zipWithIndex.foreach { case (param, index) =>
          statement.setObject(index + 1, param)
        }
        
        val resultSet = statement.executeQuery()
        val results = scala.collection.mutable.ListBuffer[T]()
        
        while (resultSet.next()) {
          results += parser(resultSet)
        }
        
        results.toList
      } finally {
        statement.close()
      }
    }
  }

  def executeUpdate(sql: String, params: List[Any] = List.empty): IO[Int] = {
    DatabaseManager.withConnection { connection =>
      val statement = connection.prepareStatement(sql)
      try {
        // 设置参数
        params.zipWithIndex.foreach { case (param, index) =>
          statement.setObject(index + 1, param)
        }
        
        statement.executeUpdate()
      } finally {
        statement.close()
      }
    }
  }

  def executeQuerySingle[T](sql: String, params: List[Any] = List.empty)(parser: ResultSet => T): IO[Option[T]] = {
    executeQuery(sql, params)(parser).map(_.headOption)
  }

  def executeInsertWithId(sql: String, params: List[Any] = List.empty): IO[String] = {
    DatabaseManager.withConnection { connection =>
      val statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
      try {
        // 设置参数
        params.zipWithIndex.foreach { case (param, index) =>
          statement.setObject(index + 1, param)
        }
        
        statement.executeUpdate()
        
        val keys = statement.getGeneratedKeys()
        if (keys.next()) {
          keys.getString(1)
        } else {
          throw new RuntimeException("Failed to get generated key")
        }
      } finally {
        statement.close()
      }
    }
  }
}
