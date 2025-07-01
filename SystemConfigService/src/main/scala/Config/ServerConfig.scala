package Config

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*

case class DatabaseConfig(
  jdbcUrl: String,
  username: String,
  password: String,
  prepStmtCacheSize: Int,
  prepStmtCacheSqlLimit: Int,
  maximumPoolSize: Int,
  connectionLiveMinutes: Int
)

case class ServerConfig(
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
  jwtSecret: Option[String] = None,
  jwtExpirationHours: Option[Int] = None,
  saltValue: Option[String] = None
) {
  // 获取JWT密钥，优先使用配置文件中的值，否则使用默认值
  def getJwtSecret: String = jwtSecret.getOrElse("SYSTEM_CONFIG_SECRET_KEY")
  
  // 获取JWT过期时间，优先使用配置文件中的值，否则使用默认值
  def getJwtExpirationHours: Int = jwtExpirationHours.getOrElse(24)
  
  // 获取盐值，优先使用配置文件中的值，否则使用默认值
  def getSaltValue: String = saltValue.getOrElse("SYSTEM_CONFIG_SALT")
  
  // 将数据库相关配置转换为 DatabaseConfig
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

object ConfigLoader {
  import io.circe.parser.*
  import java.nio.file.{Files, Paths}
  
  private var _config: Option[ServerConfig] = None
  
  def loadConfig(): ServerConfig = {
    _config match {
      case Some(config) => config
      case None =>
        val configPath = Paths.get("server_config.json")
        if (Files.exists(configPath)) {
          val configJson = new String(Files.readAllBytes(configPath))
          decode[ServerConfig](configJson) match {
            case Right(config) => 
              _config = Some(config)
              config
            case Left(error) =>
              println(s"Failed to parse config file: $error")
              getDefaultConfig()
          }
        } else {
          println("Config file not found, using default configuration")
          getDefaultConfig()
        }
    }
  }
  
  private def getDefaultConfig(): ServerConfig = {
    val defaultConfig = ServerConfig(
      serverIP = "127.0.0.1",
      serverPort = 3009,
      maximumServerConnection = 10000,
      maximumClientConnection = 10000,
      jdbcUrl = "jdbc:postgresql://localhost:5432/system_config?currentSchema=systemconfig",
      username = "db",
      password = "root",
      prepStmtCacheSize = 250,
      prepStmtCacheSqlLimit = 2048,
      maximumPoolSize = 3,
      connectionLiveMinutes = 10,
      isTest = false
    )
    _config = Some(defaultConfig)
    defaultConfig
  }
}
