package Config

import io.circe.*
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

case class ServerConfig(
  serverIP: String,
  serverPort: Int,
  maximumServerConnection: Int,
  maximumClientConnection: Int
)

case class ServiceConfig(
  serverIP: String,
  serverPort: Int,
  maximumServerConnection: Int,
  maximumClientConnection: Int,
  gitlabHost: String,
  gitlabNameSpace: String,
  jdbcUrl: String,
  username: String,
  password: String,
  prepStmtCacheSize: Int,
  prepStmtCacheSqlLimit: Int,
  maximumPoolSize: Int,
  connectionLiveMinutes: Int,
  isTest: Boolean
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
  
  def toServerConfig: ServerConfig = ServerConfig(
    serverIP = serverIP,
    serverPort = serverPort,
    maximumServerConnection = maximumServerConnection,
    maximumClientConnection = maximumClientConnection
  )
}

object ConfigLoader {
  def loadConfig(configPath: String = "server_config.json"): ServiceConfig = {
    val file = new File(configPath)
    val source = if (file.exists()) {
      Source.fromFile(file)
    } else {
      Source.fromResource(configPath)
    }
    
    try {
      val jsonStr = source.mkString
      decode[ServiceConfig](jsonStr) match {
        case Right(config) => config
        case Left(error) => throw new RuntimeException(s"配置文件解析失败: $error")
      }
    } finally {
      source.close()
    }
  }
  
  // JSON编解码器
  implicit val serviceConfigDecoder: Decoder[ServiceConfig] = Decoder.derived[ServiceConfig]
}

object Constants {
  // 通用常量
  val DEFAULT_PAGE_SIZE = 20
  val MAX_PAGE_SIZE = 100
  
  // 认证服务URL
  val AUTH_SERVICE_BASE_URL = "http://localhost:3001/api/auth"
  
  // 用户状态
  val USER_STATUS_PENDING = "PENDING"
  val USER_STATUS_ACTIVE = "ACTIVE"
  val USER_STATUS_DISABLED = "DISABLED"
  
  // 用户角色
  val USER_ROLE_STUDENT = "student"
  val USER_ROLE_COACH = "coach"
  val USER_ROLE_GRADER = "grader"
  val USER_ROLE_ADMIN = "admin"
  
  // 审核操作
  val APPROVAL_ACTION_APPROVE = "approve"
  val APPROVAL_ACTION_REJECT = "reject"
}
