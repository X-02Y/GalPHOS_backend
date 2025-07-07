package Database

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import Config.DatabaseConfig
import org.slf4j.LoggerFactory
import java.sql.{Connection, PreparedStatement, ResultSet, Statement}
import scala.util.Using

object DatabaseManager {
  private val logger = LoggerFactory.getLogger("DatabaseManager")
  private var dataSource: Option[HikariDataSource] = None

  def initializeDataSource(config: DatabaseConfig): IO[Unit] = {
    IO {
      logger.info("Initializing database connection pool...")
      
      val hikariConfig = new HikariConfig()
      hikariConfig.setJdbcUrl(config.jdbcUrl)
      hikariConfig.setUsername(config.username)
      hikariConfig.setPassword(config.password)
      hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
      hikariConfig.setMaxLifetime(config.connectionLiveMinutes * 60 * 1000L)
      
      // Connection pool settings for better performance
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
      
      dataSource = Some(new HikariDataSource(hikariConfig))
      logger.info("Database connection pool initialized successfully")
    }
  }

  def getConnection: IO[Connection] = {
    IO {
      dataSource match {
        case Some(ds) => ds.getConnection
        case None => throw new RuntimeException("DataSource not initialized")
      }
    }
  }

  // Execute query with automatic resource management
  def executeQuery[T](query: String, params: List[Any] = Nil)(mapper: ResultSet => T): IO[List[T]] = {
    Resource.fromAutoCloseable(getConnection).use { conn =>
      IO {
        Using.resource(conn.prepareStatement(query)) { stmt =>
          params.zipWithIndex.foreach { case (param, index) =>
            stmt.setObject(index + 1, param)
          }
          
          Using.resource(stmt.executeQuery()) { rs =>
            var results = List.empty[T]
            while (rs.next()) {
              results = mapper(rs) :: results
            }
            results.reverse
          }
        }
      }
    }
  }

  // Execute update with automatic resource management
  def executeUpdate(query: String, params: List[Any] = Nil): IO[Int] = {
    Resource.fromAutoCloseable(getConnection).use { conn =>
      IO {
        Using.resource(conn.prepareStatement(query)) { stmt =>
          params.zipWithIndex.foreach { case (param, index) =>
            stmt.setObject(index + 1, param)
          }
          stmt.executeUpdate()
        }
      }
    }
  }

  // Execute insert and return generated key
  def executeInsert(query: String, params: List[Any] = Nil): IO[Option[String]] = {
    Resource.fromAutoCloseable(getConnection).use { conn =>
      IO {
        Using.resource(conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) { stmt =>
          params.zipWithIndex.foreach { case (param, index) =>
            stmt.setObject(index + 1, param)
          }
          
          stmt.executeUpdate()
          
          Using.resource(stmt.getGeneratedKeys) { rs =>
            if (rs.next()) Some(rs.getString(1)) else None
          }
        }
      }
    }
  }

  def closeDataSource(): IO[Unit] = {
    IO {
      dataSource.foreach(_.close())
      dataSource = None
      logger.info("Database connection pool closed")
    }
  }
}
