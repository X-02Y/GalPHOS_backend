package Database

import cats.effect.IO
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import Config.DatabaseConfig
import org.slf4j.LoggerFactory
import java.sql.{Connection, ResultSet, PreparedStatement, Timestamp}
import javax.sql.DataSource
import java.time.LocalDateTime

object DatabaseManager {
  private val logger = LoggerFactory.getLogger("DatabaseManager")
  private var dataSource: Option[HikariDataSource] = None

  def initializeDataSource(config: DatabaseConfig): IO[Unit] = IO {
    logger.info("Initializing database connection pool...")
    
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.jdbcUrl)
    hikariConfig.setUsername(config.username)
    hikariConfig.setPassword(config.password)
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
    hikariConfig.setMaxLifetime(config.connectionLiveMinutes * 60 * 1000L)
    hikariConfig.setConnectionInitSql("SELECT 1")
    hikariConfig.setDriverClassName("org.postgresql.Driver")
    
    // Connection pool optimization configuration
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
    logger.info("Database connection pool initialized successfully")
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
        throw new RuntimeException("Database connection pool not initialized")
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
        throw new RuntimeException("Database connection pool not initialized")
    }
  }

  def shutdown(): IO[Unit] = IO {
    dataSource.foreach { ds =>
      logger.info("Shutting down database connection pool...")
      ds.close()
      logger.info("Database connection pool closed")
    }
    dataSource = None
  }
}

// Database operation utility class
object DatabaseUtils {
  private val logger = LoggerFactory.getLogger("DatabaseUtils")

  def executeQuery[T](sql: String, params: List[Any] = List.empty)(parser: ResultSet => T): IO[List[T]] = {
    DatabaseManager.withConnection { connection =>
      val statement = connection.prepareStatement(sql)
      try {
        // Set parameters
        params.zipWithIndex.foreach { case (param, index) =>
          setParameter(statement, index + 1, param)
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
        // Set parameters
        params.zipWithIndex.foreach { case (param, index) =>
          setParameter(statement, index + 1, param)
        }
        
        statement.executeUpdate()
      } finally {
        statement.close()
      }
    }
  }

  def executeUpdateWithGeneratedKeys(sql: String, params: List[Any] = List.empty): IO[String] = {
    DatabaseManager.withConnection { connection =>
      val statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
      try {
        // Set parameters
        params.zipWithIndex.foreach { case (param, index) =>
          setParameter(statement, index + 1, param)
        }
        
        statement.executeUpdate()
        
        val generatedKeys = statement.getGeneratedKeys()
        if (generatedKeys.next()) {
          generatedKeys.getString(1)
        } else {
          throw new RuntimeException("No generated keys returned")
        }
      } finally {
        statement.close()
      }
    }
  }

  def queryFirst[T](sql: String, params: List[Any] = List.empty)(parser: ResultSet => T): IO[Option[T]] = {
    executeQuery(sql, params)(parser).map(_.headOption)
  }

  def exists(sql: String, params: List[Any] = List.empty): IO[Boolean] = {
    queryFirst(sql, params)(_.getInt(1)).map(_.exists(_ > 0))
  }

  private def setParameter(statement: PreparedStatement, index: Int, param: Any): Unit = {
    param match {
      case null => statement.setNull(index, java.sql.Types.NULL)
      case s: String => statement.setString(index, s)
      case i: Int => statement.setInt(index, i)
      case l: Long => statement.setLong(index, l)
      case d: Double => statement.setDouble(index, d)
      case f: Float => statement.setFloat(index, f)
      case b: Boolean => statement.setBoolean(index, b)
      case dt: LocalDateTime => statement.setTimestamp(index, Timestamp.valueOf(dt))
      case opt: Option[_] => opt match {
        case Some(value) => setParameter(statement, index, value)
        case None => statement.setNull(index, java.sql.Types.NULL)
      }
      case _ => statement.setObject(index, param)
    }
  }

  // Helper method to convert ResultSet to LocalDateTime
  def getLocalDateTime(rs: ResultSet, columnName: String): Option[LocalDateTime] = {
    Option(rs.getTimestamp(columnName)).map(_.toLocalDateTime)
  }

  def getLocalDateTime(rs: ResultSet, columnIndex: Int): Option[LocalDateTime] = {
    Option(rs.getTimestamp(columnIndex)).map(_.toLocalDateTime)
  }
}
