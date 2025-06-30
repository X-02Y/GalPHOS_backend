package Config

import scala.io.Source
import spray.json._
import DefaultJsonProtocol._
import java.io.FileNotFoundException

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
  fileStoragePath: String,
  maxFileSize: Long,
  allowedFileTypes: List[String]
)

object ServerConfig {
  // JSON格式定义
  implicit val serverConfigFormat: RootJsonFormat[ServerConfig] = jsonFormat15(ServerConfig.apply)
  
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
      serverPort = 3008,
      maximumServerConnection = 10000,
      maximumClientConnection = 10000,
      jdbcUrl = "jdbc:postgresql://localhost:5432/file_storage?currentSchema=filestorage",
      username = "db",
      password = "root",
      prepStmtCacheSize = 250,
      prepStmtCacheSqlLimit = 2048,
      maximumPoolSize = 5,
      connectionLiveMinutes = 10,
      isTest = false,
      fileStoragePath = "./storage/files",
      maxFileSize = 104857600L, // 100MB
      allowedFileTypes = List(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "jpg", "jpeg", "png", "gif", "bmp",
        "txt", "csv", "zip", "rar"
      )
    )
  }
}

object Constants {
  val SCHEMA_NAME = "filestorage"
  val DEFAULT_PAGE_SIZE = 20
  val MAX_PAGE_SIZE = 100
  val TEMP_FILE_EXPIRE_HOURS = 24
  val MAX_UPLOAD_RETRIES = 3
}
