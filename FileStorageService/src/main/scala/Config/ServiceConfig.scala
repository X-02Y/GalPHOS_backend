package Config

import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import scala.io.Source
import java.io.File

case class DatabaseConfig(
  jdbcUrl: String,
  username: String,
  password: String,
  prepStmtCacheSize: Int,
  prepStmtCacheSqlLimit: Int,
  maximumPoolSize: Int,
  connectionLiveMinutes: Int
)

case class StorageConfig(
  localPath: String,
  baseUrl: String,
  maxFileSize: Map[String, Long],
  allowedTypes: Map[String, List[String]]
)

case class ServiceConfig(
  serverIP: String,
  serverPort: Int,
  maximumServerConnection: Int,
  maximumClientConnection: Int,
  jdbcUrl: String,
  username: String,
  password: String,
  prepStmtCacheSize: Int,
  prepStmtCacheSqlLimit: Int,
  maximumPoolSize: Int,
  connectionLiveMinutes: Int,
  isTest: Boolean,
  storage: StorageConfig,
  jwtSecret: String,
  jwtExpiration: Int,
  internalApiKey: String
) {
  def toDatabaseConfig: DatabaseConfig = DatabaseConfig(
    jdbcUrl = jdbcUrl,
    username = username,
    password = password,
    prepStmtCacheSize = prepStmtCacheSize,
    prepStmtCacheSqlLimit = prepStmtCacheSqlLimit,
    maximumPoolSize = maximumPoolSize,
    connectionLiveMinutes = connectionLiveMinutes
  )
}

// JSON decoders - auto derivation
// The decoders are automatically derived due to the import io.circe.generic.auto.*

object ConfigLoader {
  def loadConfig(): ServiceConfig = {
    val configFile = new File("server_config.json")
    
    if (!configFile.exists()) {
      throw new RuntimeException("Configuration file 'server_config.json' not found")
    }
    
    val configSource = Source.fromFile(configFile)
    try {
      val configContent = configSource.mkString
      
      parse(configContent) match {
        case Right(json) =>
          json.as[ServiceConfig] match {
            case Right(config) => config
            case Left(error) => 
              throw new RuntimeException(s"Failed to parse configuration: $error")
          }
        case Left(error) =>
          throw new RuntimeException(s"Failed to parse JSON: $error")
      }
    } finally {
      configSource.close()
    }
  }
}
