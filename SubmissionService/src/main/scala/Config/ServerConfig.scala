package Config

import scala.io.Source
import spray.json._
import DefaultJsonProtocol._
import java.io.FileNotFoundException

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
  authServiceUrl: String,
  examServiceUrl: String,
  fileStorageServiceUrl: String,
  maxFileSize: Long,
  allowedFileTypes: List[String]
) {
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

object ServerConfig {
  // JSON格式定义
  implicit val databaseConfigFormat: RootJsonFormat[DatabaseConfig] = jsonFormat7(DatabaseConfig.apply)
  implicit val serverConfigFormat: RootJsonFormat[ServerConfig] = jsonFormat17(ServerConfig.apply)
  
  def loadConfig(configPath: String = "server_config.json"): ServerConfig = {
    try {
      val source = Source.fromFile(configPath)
      val jsonString = source.mkString
      source.close()
      
      val json = jsonString.parseJson
      json.convertTo[ServerConfig]
    } catch {
      case _: FileNotFoundException =>
        println(s"配置文件 $configPath 未找到，使用默认配置")
        getDefaultConfig()
      case ex: Exception =>
        println(s"配置文件解析失败: ${ex.getMessage}，使用默认配置")
        getDefaultConfig()
    }
  }
  
  private def getDefaultConfig(): ServerConfig = {
    ServerConfig(
      serverIP = "127.0.0.1",
      serverPort = 3004,
      maximumServerConnection = 10000,
      maximumClientConnection = 10000,
      jdbcUrl = "jdbc:postgresql://localhost:5432/galphos?currentSchema=submissionservice",
      username = "db",
      password = "root",
      prepStmtCacheSize = 250,
      prepStmtCacheSqlLimit = 2048,
      maximumPoolSize = 10,
      connectionLiveMinutes = 10,
      isTest = false,
      authServiceUrl = "http://localhost:3001",
      examServiceUrl = "http://localhost:3003",
      fileStorageServiceUrl = "http://localhost:3008",
      maxFileSize = 10485760L, // 10MB
      allowedFileTypes = List("jpg", "jpeg", "png", "pdf")
    )
  }

  // 兼容旧的方法名
  def load(configPath: String = "server_config.json"): ServerConfig = loadConfig(configPath)
}
